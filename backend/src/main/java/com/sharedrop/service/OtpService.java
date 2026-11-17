package com.sharedrop.service;

import com.sharedrop.dto.request.VerifyOtpRequest;
import com.sharedrop.dto.response.FileInfoResponse;
import com.sharedrop.dto.response.VerifyOtpResponse;
import com.sharedrop.dto.response.ResendOtpResponse;
import com.sharedrop.dto.response.TextEntryResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.repository.SharedTextRepository;
import com.sharedrop.security.OtpGenerator;
import com.sharedrop.security.TokenGenerator;
import com.sharedrop.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpRepository otpRepository;
    private final ShareRepository shareRepository;
    private final SharedTextRepository textRepository;
    private final AuditLogService auditLogService;

    private final int maxAttempts;
    private final int lockoutMinutes;
    private final int otpExpirationMinutes;

    public OtpService(
            OtpRepository otpRepository,
            ShareRepository shareRepository,
            SharedTextRepository textRepository,
            AuditLogService auditLogService,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.lockout-minutes:15}") int lockoutMinutes,
            @Value("${app.otp.expiration-minutes:10}") int otpExpirationMinutes) {
        this.otpRepository = otpRepository;
        this.shareRepository = shareRepository;
        this.textRepository = textRepository;
        this.auditLogService = auditLogService;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    /**
     * Issues a replacement OTP only after the current OTP expires.
     * Requires the sender-only secret returned when the share was created.
     */
    @Transactional
    public ResendOtpResponse resendOtp(String rawToken, String rawResendSecret) {
        String tokenHash = HashUtil.sha256(rawToken);
        Share share = shareRepository.findByPublicToken(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found for token"));

        if (share.isExpired() || share.getShareStatus() != ShareStatus.ACTIVE) {
            throw new ShareExpiredException("This share is no longer active. A new share is required.");
        }

        OtpVerification verification = otpRepository.findByShareId(share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No OTP verification configured for this share"));

        if (rawResendSecret == null || rawResendSecret.isBlank()
                || verification.getResendSecretHash() == null
                || !HashUtil.sha256(rawResendSecret).equals(verification.getResendSecretHash())) {
            throw new InvalidOtpException("Sender authorization failed. Use the original sender session.");
        }

        if (!verification.isExpired()) {
            long secondsLeft = Duration.between(Instant.now(), verification.getExpiresAt()).getSeconds();
            throw new InvalidOtpException("The current OTP is still valid. Resend is available after it expires in " + Math.max(1, secondsLeft) + " seconds.");
        }

        String newOtp = OtpGenerator.generate();
        String newResendSecret = TokenGenerator.generateToken();

        verification.setOtpHash(HashUtil.bcryptHash(newOtp));
        verification.setResendSecretHash(HashUtil.sha256(newResendSecret));
        verification.setExpiresAt(Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes)));
        verification.setFailedAttempts(0);
        verification.setLockedUntil(null);
        verification.setVerifiedAt(null);
        otpRepository.save(verification);

        ResendOtpResponse response = new ResendOtpResponse();
        response.setOtp(newOtp);
        response.setResendSecret(newResendSecret);
        response.setExpiresAt(verification.getExpiresAt().toString());
        return response;
    }

    /**
     * Verifies the recipient-submitted OTP against the secure hash in database.
     * Enforces rate limiting, attempt counters, brute force lockout, and expiration.
     */
    @Transactional
    public VerifyOtpResponse verifyOtp(String rawToken, VerifyOtpRequest request) {
        String tokenHash = HashUtil.sha256(rawToken);
        Share share = shareRepository.findByPublicToken(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found for token"));

        if (share.isExpired()) {
            if (share.getShareStatus() == ShareStatus.ACTIVE) {
                share.setShareStatus(ShareStatus.EXPIRED);
                shareRepository.save(share);
            }
            throw new ShareExpiredException("This share has expired.");
        }

        if (share.getShareStatus() == ShareStatus.REVOKED) {
            throw new ShareExpiredException("This share has been revoked.");
        }

        if (share.getShareStatus() == ShareStatus.COMPLETED) {
            throw new ShareExpiredException("This share has reached its maximum download limit.");
        }

        OtpVerification otpVerification = otpRepository.findByShareId(share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No OTP verification configured for this share"));

        // Check lockout
        if (otpVerification.isLocked()) {
            long minutesLeft = Duration.between(Instant.now(), otpVerification.getLockedUntil()).toMinutes() + 1;
            log.warn("Attempt on locked OTP verification for share id={}", share.getId());
            throw new InvalidOtpException("Verification is temporarily locked due to too many failed attempts. Try again in " + minutesLeft + " minutes.");
        }

        // Check expiration
        if (otpVerification.isExpired()) {
            log.warn("Expired OTP submitted for share id={}", share.getId());
            throw new InvalidOtpException("OTP has expired. Please request a new share.");
        }

        // If password lock is enabled on the share, verify password
        if (share.getPasswordHash() != null && !share.getPasswordHash().isBlank()) {
            if (request.getPassword() == null || !HashUtil.bcryptVerify(request.getPassword(), share.getPasswordHash())) {
                throw new InvalidOtpException("Incorrect access passphrase provided.");
            }
        }

        // Verify OTP hash
        boolean isMatch = HashUtil.bcryptVerify(request.getOtp(), otpVerification.getOtpHash());
        if (!isMatch) {
            otpVerification.incrementFailedAttempts();
            int remaining = maxAttempts - otpVerification.getFailedAttempts();

            if (remaining <= 0) {
                otpVerification.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockoutMinutes)));
                otpRepository.save(otpVerification);
                log.warn("Locking OTP verification for share id={} due to {} failed attempts", share.getId(), maxAttempts);
                throw new InvalidOtpException("Too many incorrect attempts. Verification locked for " + lockoutMinutes + " minutes.", 0);
            } else {
                otpRepository.save(otpVerification);
                log.info("Failed OTP attempt for share id={}, remaining={}", share.getId(), remaining);
                throw new InvalidOtpException("Invalid OTP. " + remaining + " attempts remaining.", remaining);
            }
        }

        // Successful verification
        otpVerification.setVerifiedAt(Instant.now());
        otpVerification.setFailedAttempts(0);
        otpVerification.setLockedUntil(null);
        otpRepository.save(otpVerification);

        auditLogService.record(share, "OTP_VERIFIED", "Recipient successfully verified the session OTP.");
        log.info("OTP verified successfully for share id={}", share.getId());

        VerifyOtpResponse response = new VerifyOtpResponse();
        response.setShareType(share.getShareType().name());
        List<FileInfoResponse> files = new ArrayList<>();
        if (share.getFiles() != null) {
            share.getFiles().forEach(f -> files.add(new FileInfoResponse(
                    f.getId(), f.getOriginalFilename(), f.getContentType(), f.getFileSize())));
        }
        response.setFiles(files);
        List<TextEntryResponse> textEntries = new ArrayList<>();
        textRepository.findByShareIdOrderByCreatedAtAsc(share.getId()).forEach(entry -> textEntries.add(
                new TextEntryResponse(entry.getId(), entry.getTitle(), entry.getContent(), entry.getFormat(), entry.getCreatedAt().toString())));
        response.setTextEntries(textEntries);
        if (textEntries.isEmpty()) response.setTextContent(share.getTextContent());

        return response;
    }
}
