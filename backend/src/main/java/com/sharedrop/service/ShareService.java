package com.sharedrop.service;

import com.sharedrop.dto.request.CreateFileShareRequest;
import com.sharedrop.dto.request.CreateTextShareRequest;
import com.sharedrop.dto.response.CreateShareResponse;
import com.sharedrop.dto.response.FileInfoResponse;
import com.sharedrop.dto.response.ShareStatusResponse;
import com.sharedrop.dto.response.ShareManagementResponse;
import com.sharedrop.dto.response.TextEntryResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.entity.SharedTextEntry;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.enums.UploadStatus;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.repository.SharedTextRepository;
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
    private final SharedTextRepository textRepository;
    private final AuditLogService auditLogService;
    private final AesGcmEncryptionService encryptionService;

    private final String baseUrl;
    private final int otpExpirationMinutes;
    private final int defaultMaxDownloads;

    public ShareService(
            ShareRepository shareRepository,
            FileRepository fileRepository,
            OtpRepository otpRepository,
            StorageService storageService,
            FileValidator fileValidator,
            SharedTextRepository textRepository,
            AuditLogService auditLogService,
            AesGcmEncryptionService encryptionService,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.otp.expiration-minutes:10}") int otpExpirationMinutes,
            @Value("${app.share.max-downloads-default:100}") int defaultMaxDownloads) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.otpRepository = otpRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.textRepository = textRepository;
        this.auditLogService = auditLogService;
        this.encryptionService = encryptionService;
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

        // Generate a public token and a separate sender-only management credential.
        String rawToken = TokenGenerator.generateToken();
        String rawOwnerSecret = TokenGenerator.generateToken();
        String tokenHash = HashUtil.sha256(rawToken);

        boolean isBurn = "burn".equalsIgnoreCase(request.getExpiration());
        Instant expiresAt = calculateExpiration(request.getExpiration());

        // Create share entity
        Share share = new Share();
        share.setPublicToken(tokenHash);
        share.setOwnerSecretHash(HashUtil.sha256(rawOwnerSecret));
        share.setShareType(ShareType.MIXED);
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

        // Create OTP and a separate sender-only credential for secure OTP resends.
        String rawOtp = OtpGenerator.generate();
        String rawResendSecret = TokenGenerator.generateToken();
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setShare(savedShare);
        otpVerification.setOtpHash(HashUtil.bcryptHash(rawOtp));
        otpVerification.setResendSecretHash(HashUtil.sha256(rawResendSecret));
        otpVerification.setExpiresAt(Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes)));
        otpVerification.setFailedAttempts(0);
        otpRepository.save(otpVerification);

        auditLogService.record(savedShare, "SESSION_CREATED", "Sharing session created with " + files.size() + " file(s).");
        log.info("Created file share: id={}, files={}, expiresAt={}", savedShare.getId(), files.size(), expiresAt);

        CreateShareResponse response = new CreateShareResponse();
        response.setShareToken(rawToken);
        response.setShareUrl(baseUrl + "/d/" + rawToken);
        response.setOtp(rawOtp);
        response.setResendSecret(rawResendSecret);
        response.setOwnerSecret(rawOwnerSecret);
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        response.setShareType(ShareType.MIXED.name());
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
        String rawOwnerSecret = TokenGenerator.generateToken();
        String tokenHash = HashUtil.sha256(rawToken);

        boolean isBurn = "burn".equalsIgnoreCase(request.getExpiration());
        Instant expiresAt = calculateExpiration(request.getExpiration());

        Share share = new Share();
        share.setPublicToken(tokenHash);
        share.setOwnerSecretHash(HashUtil.sha256(rawOwnerSecret));
        share.setShareType(ShareType.MIXED);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(expiresAt);
        share.setMaxDownloads(isBurn ? 1 : defaultMaxDownloads);
        share.setDownloadCount(0);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            share.setPasswordHash(HashUtil.bcryptHash(request.getPassword()));
        }

        Share savedShare = shareRepository.save(share);

        SharedTextEntry textEntry = new SharedTextEntry();
        textEntry.setShare(savedShare);
        textEntry.setTitle("Text entry");
        textEntry.setContent(encryptionService.encryptText(request.getTextContent()));
        textEntry.setFormat(request.getSnippetFormat() == null ? "text" : request.getSnippetFormat());
        textRepository.save(textEntry);

        // Create OTP and a separate sender-only credential for secure OTP resends.
        String rawOtp = OtpGenerator.generate();
        String rawResendSecret = TokenGenerator.generateToken();
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setShare(savedShare);
        otpVerification.setOtpHash(HashUtil.bcryptHash(rawOtp));
        otpVerification.setResendSecretHash(HashUtil.sha256(rawResendSecret));
        otpVerification.setExpiresAt(Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes)));
        otpVerification.setFailedAttempts(0);
        otpRepository.save(otpVerification);

        auditLogService.record(savedShare, "SESSION_CREATED", "Sharing session created with one text entry.");
        log.info("Created text share: id={}, length={}, expiresAt={}", savedShare.getId(), request.getTextContent().length(), expiresAt);

        CreateShareResponse response = new CreateShareResponse();
        response.setShareToken(rawToken);
        response.setShareUrl(baseUrl + "/d/" + rawToken);
        response.setOtp(rawOtp);
        response.setResendSecret(rawResendSecret);
        response.setOwnerSecret(rawOwnerSecret);
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        response.setShareType(ShareType.MIXED.name());
        response.setFiles(new ArrayList<>());
        return response;
    }

    @Transactional
    public void addFilesToSession(String rawToken, String ownerSecret, List<MultipartFile> files) {
        Share share = findOwnedActiveShare(rawToken, ownerSecret);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Select at least one file.");
        List<SharedFile> existing = fileRepository.findByShareId(share.getId());
        if (existing.size() + files.size() > fileValidator.getMaxFilesPerShare()) {
            throw new IllegalArgumentException("Maximum allowed files per session is " + fileValidator.getMaxFilesPerShare());
        }
        for (MultipartFile file : files) {
            if (file.isEmpty() || !fileValidator.isAllowedSize(file.getSize())
                    || !fileValidator.isAllowedExtension(file.getOriginalFilename())) {
                throw new IllegalArgumentException("Invalid or unsupported file: " + file.getOriginalFilename());
            }
        }
        for (MultipartFile file : files) {
            String filename = fileValidator.sanitizeFilename(file.getOriginalFilename());
            String key = share.getId() + "/" + UUID.randomUUID() + "_" + filename;
            storageService.store(file, key);
            SharedFile saved = new SharedFile();
            saved.setShare(share);
            saved.setOriginalFilename(filename);
            saved.setStorageKey(key);
            saved.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            saved.setFileSize(file.getSize());
            saved.setUploadStatus(UploadStatus.COMPLETED);
            fileRepository.save(saved);
        }
        auditLogService.record(share, "FILES_ADDED", files.size() + " file(s) added to the session.");
    }

    @Transactional
    public void addTextToSession(String rawToken, String ownerSecret, CreateTextShareRequest request) {
        Share share = findOwnedActiveShare(rawToken, ownerSecret);
        if (request.getTextContent() == null || request.getTextContent().isBlank()) {
            throw new IllegalArgumentException("Text content cannot be empty.");
        }
        byte[] bytes = request.getTextContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!fileValidator.isAllowedTextSize(bytes.length)) {
            throw new IllegalArgumentException("Text content exceeds the maximum allowed size.");
        }
        SharedTextEntry entry = new SharedTextEntry();
        entry.setShare(share);
        entry.setTitle(request.getTitle() == null || request.getTitle().isBlank() ? "Text entry" : request.getTitle());
        entry.setContent(encryptionService.encryptText(request.getTextContent()));
        entry.setFormat(request.getSnippetFormat() == null ? "text" : request.getSnippetFormat());
        textRepository.save(entry);
        auditLogService.record(share, "TEXT_ADDED", "A text entry was added to the session.");
    }

    @Transactional(readOnly = true)
    public ShareManagementResponse getManagementDetails(String rawToken, String ownerSecret) {
        Share share = findOwnedActiveShare(rawToken, ownerSecret);
        ShareManagementResponse response = new ShareManagementResponse();
        response.setStatus(share.getShareStatus().name());
        response.setCreatedAt(share.getCreatedAt().toString());
        response.setExpiresAt(share.getExpiresAt().toString());
        response.setDownloadCount(share.getDownloadCount());
        response.setMaxDownloads(share.getMaxDownloads());
        List<FileInfoResponse> files = new ArrayList<>();
        fileRepository.findByShareId(share.getId()).forEach(file -> files.add(
                new FileInfoResponse(file.getId(), file.getOriginalFilename(), file.getContentType(), file.getFileSize())));
        response.setFiles(files);
        List<TextEntryResponse> texts = new ArrayList<>();
        textRepository.findByShareIdOrderByCreatedAtAsc(share.getId()).forEach(entry -> texts.add(
                new TextEntryResponse(entry.getId(), entry.getTitle(), encryptionService.decryptText(entry.getContent()), entry.getFormat(), entry.getCreatedAt().toString())));
        response.setTextEntries(texts);
        return response;
    }

    @Transactional(readOnly = true)
    public Share findOwnedActiveShare(String rawToken, String ownerSecret) {
        String tokenHash = HashUtil.sha256(rawToken);
        Share share = shareRepository.findByPublicToken(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found for token"));
        if (ownerSecret == null || share.getOwnerSecretHash() == null
                || !HashUtil.sha256(ownerSecret).equals(share.getOwnerSecretHash())) {
            throw new com.sharedrop.exception.InvalidOtpException("Sender authorization failed.");
        }
        if (!share.isActive()) throw new ShareExpiredException("This session is no longer active.");
        return share;
    }

    @Transactional
    public void revokeShare(String rawToken, String ownerSecret) {
        Share share = findOwnedActiveShare(rawToken, ownerSecret);
        share.setShareStatus(ShareStatus.REVOKED);
        auditLogService.record(share, "SESSION_REVOKED", "Sender revoked the sharing session.");
        shareRepository.save(share);
        if (share.getFiles() != null) {
            for (SharedFile file : share.getFiles()) {
                try { storageService.delete(file.getStorageKey()); }
                catch (Exception e) { log.warn("Failed to delete revoked file {}", file.getStorageKey(), e); }
            }
        }
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
