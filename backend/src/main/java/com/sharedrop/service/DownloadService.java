package com.sharedrop.service;

import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final OtpRepository otpRepository;
    private final StorageService storageService;

    public DownloadService(
            ShareRepository shareRepository,
            FileRepository fileRepository,
            OtpRepository otpRepository,
            StorageService storageService) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.otpRepository = otpRepository;
        this.storageService = storageService;
    }

    /**
     * Authorizes and streams a file download.
     * Enforces active state, OTP verification, and download quotas (e.g. burn after 1 download).
     */
    @Transactional
    public Resource downloadFile(String rawToken, Long fileId) {
        Share share = getValidatedShare(rawToken);

        // Verify that recipient has verified OTP before allowing download
        OtpVerification otp = otpRepository.findByShareId(share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("OTP record not found for share"));

        if (!otp.isVerified()) {
            log.warn("Unauthorized download attempt without OTP verification for share id={}", share.getId());
            throw new InvalidOtpException("You must verify the 6-digit OTP before downloading files.");
        }

        // Verify download quota
        if (share.getDownloadCount() >= share.getMaxDownloads()) {
            share.setShareStatus(ShareStatus.COMPLETED);
            shareRepository.save(share);
            throw new ShareExpiredException("This share has reached its maximum download limit.");
        }

        // Retrieve file
        SharedFile file = fileRepository.findByIdAndShareId(fileId, share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File id " + fileId + " not found in this share"));

        // Increment download counter
        share.incrementDownloadCount();
        if (share.getDownloadCount() >= share.getMaxDownloads()) {
            share.setShareStatus(ShareStatus.COMPLETED);
            log.info("Share id={} marked as COMPLETED (reached max downloads: {})", share.getId(), share.getMaxDownloads());
        }
        shareRepository.save(share);

        log.info("Authorized download for file id={}, share id={}, current downloads={}/{}",
                file.getId(), share.getId(), share.getDownloadCount(), share.getMaxDownloads());

        return storageService.loadAsResource(file.getStorageKey());
    }

    @Transactional(readOnly = true)
    public String getContentType(String rawToken, Long fileId) {
        Share share = getValidatedShare(rawToken);
        SharedFile file = fileRepository.findByIdAndShareId(fileId, share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File id " + fileId + " not found in this share"));
        return file.getContentType();
    }

    @Transactional(readOnly = true)
    public String getFilename(String rawToken, Long fileId) {
        Share share = getValidatedShare(rawToken);
        SharedFile file = fileRepository.findByIdAndShareId(fileId, share.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File id " + fileId + " not found in this share"));
        return file.getOriginalFilename();
    }

    private Share getValidatedShare(String rawToken) {
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

        return share;
    }
}
