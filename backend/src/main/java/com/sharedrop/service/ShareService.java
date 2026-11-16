package com.sharedrop.service;

import com.sharedrop.dto.request.CreateFileShareRequest;
import com.sharedrop.dto.request.CreateTextShareRequest;
import com.sharedrop.dto.response.CreateShareResponse;
import com.sharedrop.dto.response.FileInfoResponse;
import com.sharedrop.dto.response.ShareStatusResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.enums.UploadStatus;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.security.OtpGenerator;
import com.sharedrop.security.TokenGenerator;
import com.sharedrop.util.FileValidator;
import com.sharedrop.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final OtpRepository otpRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;

    private final String baseUrl;
    private final int otpExpirationMinutes;
    private final int defaultMaxDownloads;

    public ShareService(
            ShareRepository shareRepository,
            FileRepository fileRepository,
            OtpRepository otpRepository,
            StorageService storageService,
            FileValidator fileValidator,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.otp.expiration-minutes:30}") int otpExpirationMinutes,
            @Value("${app.share.max-downloads-default:100}") int defaultMaxDownloads) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.otpRepository = otpRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.defaultMaxDownloads = defaultMaxDownloads;
    }

    /**
     * Creates a new file share with one or multiple files.
     */
    @Transactional
    public CreateShareResponse createFileShare(List<MultipartFile> files, CreateFileShareRequest request) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be provided for a file share.");
        }

        if (!fileValidator.isAllowedFileCount(files.size())) {
            throw new IllegalArgumentException("Maximum allowed files per share is " + fileValidator.getMaxFilesPerShare());
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Cannot upload empty file: " + file.getOriginalFilename());
            }
            if (!fileValidator.isAllowedSize(file.getSize())) {
                throw new IllegalArgumentException("File size exceeds limit (" + fileValidator.getMaxFileSize() + " bytes): " + file.getOriginalFilename());
            }
            if (!fileValidator.isAllowedExtension(file.getOriginalFilename())) {
                throw new IllegalArgumentException("File type not allowed for: " + file.getOriginalFilename());
            }
        }

        // Generate token and OTP
        String rawToken = TokenGenerator.generateToken();
        String tokenHash = HashUtil.sha256(rawToken);

        boolean isBurn = "burn".equalsIgnoreCase(request.getExpiration());
        Instant expiresAt = calculateExpiration(request.getExpiration());

        // Create share entity
        Share share = new Share();
        share.setPublicToken(tokenHash);
        share.setShareType(ShareType.FILE);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(expiresAt);
        share.setMaxDownloads(isBurn ? 1 : defaultMaxDownloads);
        share.setDownloadCount(0);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            share.setPasswordHash(HashUtil.bcryptHash(request.getPassword()));
        }

        Share savedShare = shareRepository.save(share);

        // Store each file using streaming storage
        List<FileInfoResponse> fileInfoList = new ArrayList<>();
        for (MultipartFile file : files) {
            String sanitizedFilename = fileValidator.sanitizeFilename(file.getOriginalFilename());
            String storageKey = savedShare.getId() + "/" + UUID.randomUUID() + "_" + sanitizedFilename;

            storageService.store(file, storageKey);

            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            SharedFile sharedFile = new SharedFile();
            sharedFile.setShare(savedShare);
            sharedFile.setOriginalFilename(sanitizedFilename);
            sharedFile.setStorageKey(storageKey);
            sharedFile.setContentType(contentType);
            sharedFile.setFileSize(file.getSize());
            sharedFile.setUploadStatus(UploadStatus.COMPLETED);

            SharedFile savedFile = fileRepository.save(sharedFile);
            fileInfoList.add(new FileInfoResponse(
                    savedFile.getId(),
                    savedFile.getOriginalFilename(),
                    savedFile.getContentType(),
                    savedFile.getFileSize()
            ));
        }

        // Create OTP
        String rawOtp = OtpGenerator.generate();
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setShare(savedShare);
        otpVerification.setOtpHash(HashUtil.bcryptHash(rawOtp));
        otpVerification.setExpiresAt(Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes)));
        otpVerification.setFailedAttempts(0);
        otpRepository.save(otpVerification);

        log.info("Created file share: id={}, files={}, expiresAt={}", savedShare.getId(), files.size(), expiresAt);

        CreateShareResponse response = new CreateShareResponse();
        response.setShareToken(rawToken);
        response.setShareUrl(baseUrl + "/d/" + rawToken);
        response.setOtp(rawOtp);
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        response.setShareType(ShareType.FILE.name());
        response.setFiles(fileInfoList);
        return response;
    }

    /**
     * Creates a new text/code share.
     */
    @Transactional
    public CreateShareResponse createTextShare(CreateTextShareRequest request) {
        if (request.getTextContent() == null || request.getTextContent().isBlank()) {
            throw new IllegalArgumentException("Text content cannot be empty.");
        }

        byte[] textBytes = request.getTextContent().getBytes();
        if (!fileValidator.isAllowedTextSize(textBytes.length)) {
            throw new IllegalArgumentException("Text content exceeds maximum allowed size (" + fileValidator.getMaxTextSize() + " bytes).");
        }

        String rawToken = TokenGenerator.generateToken();
        String tokenHash = HashUtil.sha256(rawToken);

        boolean isBurn = "burn".equalsIgnoreCase(request.getExpiration());
        Instant expiresAt = calculateExpiration(request.getExpiration());

        Share share = new Share();
        share.setPublicToken(tokenHash);
        share.setShareType(ShareType.TEXT);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setTextContent(request.getTextContent());
        share.setExpiresAt(expiresAt);
        share.setMaxDownloads(isBurn ? 1 : defaultMaxDownloads);
        share.setDownloadCount(0);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            share.setPasswordHash(HashUtil.bcryptHash(request.getPassword()));
        }

        Share savedShare = shareRepository.save(share);

        // Create OTP
        String rawOtp = OtpGenerator.generate();
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setShare(savedShare);
        otpVerification.setOtpHash(HashUtil.bcryptHash(rawOtp));
        otpVerification.setExpiresAt(Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes)));
        otpVerification.setFailedAttempts(0);
        otpRepository.save(otpVerification);

        log.info("Created text share: id={}, length={}, expiresAt={}", savedShare.getId(), request.getTextContent().length(), expiresAt);

        CreateShareResponse response = new CreateShareResponse();
        response.setShareToken(rawToken);
        response.setShareUrl(baseUrl + "/d/" + rawToken);
        response.setOtp(rawOtp);
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        response.setShareType(ShareType.TEXT.name());
        response.setFiles(new ArrayList<>());
        return response;
    }

    /**
     * Finds active share by raw token, validating its expiration status.
     */
    @Transactional
    public Share findByToken(String rawToken) {
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
            throw new ShareExpiredException("This share has reached its download limit.");
        }

        return share;
    }

    /**
     * Retrieves status of a share by token without exposing protected content.
     */
    @Transactional
    public ShareStatusResponse getShareStatus(String rawToken) {
        String tokenHash = HashUtil.sha256(rawToken);
        Share share = shareRepository.findByPublicToken(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found for token"));

        // Check if expired and update status
        if (share.isExpired() && share.getShareStatus() == ShareStatus.ACTIVE) {
            share.setShareStatus(ShareStatus.EXPIRED);
            shareRepository.save(share);
        }

        ShareStatusResponse response = new ShareStatusResponse();
        response.setStatus(share.getShareStatus().name());
        response.setShareType(share.getShareType().name());
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(share.getExpiresAt()));
        response.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(share.getCreatedAt()));
        response.setDownloadCount(share.getDownloadCount());
        response.setMaxDownloads(share.getMaxDownloads());
        return response;
    }

    /**
     * Revokes a share immediately and deletes underlying files from storage.
     */
    @Transactional
    public void revokeShare(String rawToken) {
        String tokenHash = HashUtil.sha256(rawToken);
        Share share = shareRepository.findByPublicToken(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found for token"));

        share.setShareStatus(ShareStatus.REVOKED);

        if (share.getFiles() != null) {
            for (SharedFile file : share.getFiles()) {
                try {
                    storageService.delete(file.getStorageKey());
                } catch (Exception e) {
                    log.warn("Failed to delete file on revoke: {}", file.getStorageKey(), e);
                }
            }
        }

        shareRepository.save(share);
        log.info("Revoked share id={}", share.getId());
    }

    private Instant calculateExpiration(String expiration) {
        if (expiration == null || expiration.isBlank()) {
            return Instant.now().plus(Duration.ofHours(24));
        }

        return switch (expiration.toLowerCase()) {
            case "10m" -> Instant.now().plus(Duration.ofMinutes(10));
            case "1h" -> Instant.now().plus(Duration.ofHours(1));
            case "24h" -> Instant.now().plus(Duration.ofHours(24));
            case "7d" -> Instant.now().plus(Duration.ofDays(7));
            case "burn" -> Instant.now().plus(Duration.ofHours(24)); // Burn after 1 download, expires in 24h if unopened
            default -> Instant.now().plus(Duration.ofHours(24));
        };
    }
}
