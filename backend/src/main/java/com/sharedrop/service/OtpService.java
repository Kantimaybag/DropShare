package com.sharedrop.service;

import com.sharedrop.dto.request.VerifyOtpRequest;
import com.sharedrop.dto.response.FileInfoResponse;
import com.sharedrop.dto.response.VerifyOtpResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
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

    private final int maxAttempts;
    private final int lockoutMinutes;

    public OtpService(
            OtpRepository otpRepository,
            ShareRepository shareRepository,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.lockout-minutes:15}") int lockoutMinutes) {
        this.otpRepository = otpRepository;
        this.shareRepository = shareRepository;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
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

        log.info("OTP verified successfully for share id={}", share.getId());

        VerifyOtpResponse response = new VerifyOtpResponse();
        response.setShareType(share.getShareType().name());

        if (share.getShareType() == ShareType.TEXT) {
            response.setTextContent(share.getTextContent());
            response.setFiles(new ArrayList<>());
        } else {
            List<FileInfoResponse> files = new ArrayList<>();
            if (share.getFiles() != null) {
                share.getFiles().forEach(f -> files.add(new FileInfoResponse(
                        f.getId(),
                        f.getOriginalFilename(),
                        f.getContentType(),
                        f.getFileSize()
                )));
            }
            response.setFiles(files);
        }

        return response;
    }
}
