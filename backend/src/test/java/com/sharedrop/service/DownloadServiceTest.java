package com.sharedrop.service;

import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private StorageService storageService;

    private DownloadService downloadService;

    @BeforeEach
    void setUp() {
        downloadService = new DownloadService(shareRepository, fileRepository, otpRepository, storageService);
    }

    @Test
    @DisplayName("Should prevent download if recipient has not verified OTP")
    void testDownloadUnauthorizedWithoutOtp() {
        String rawToken = "rawToken123";
        Long fileId = 10L;

        Share share = new Share();
        share.setId(1L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        OtpVerification otp = new OtpVerification();
        otp.setVerifiedAt(null); // NOT verified!

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(1L)).thenReturn(Optional.of(otp));

        assertThrows(InvalidOtpException.class, () ->
                downloadService.downloadFile(rawToken, fileId)
        );
    }

    @Test
    @DisplayName("Should permit download when OTP is verified and quota is available")
    void testDownloadSuccess() {
        String rawToken = "rawToken123";
        Long fileId = 10L;

        Share share = new Share();
        share.setId(2L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));
        share.setMaxDownloads(100);
        share.setDownloadCount(0);

        OtpVerification otp = new OtpVerification();
        otp.setVerifiedAt(Instant.now().minusSeconds(10)); // Verified!

        SharedFile file = new SharedFile();
        file.setId(fileId);
        file.setOriginalFilename("contract.pdf");
        file.setStorageKey("2/uuid_contract.pdf");
        file.setContentType("application/pdf");

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(2L)).thenReturn(Optional.of(otp));
        when(fileRepository.findByIdAndShareId(fileId, 2L)).thenReturn(Optional.of(file));
        when(storageService.loadAsResource("2/uuid_contract.pdf"))
                .thenReturn(new ByteArrayResource("dummy file content".getBytes()));

        Resource resource = downloadService.downloadFile(rawToken, fileId);

        assertNotNull(resource);
        assertEquals(1, share.getDownloadCount());
        verify(shareRepository).save(share);
        verify(storageService).loadAsResource("2/uuid_contract.pdf");
    }

    @Test
    @DisplayName("Should enforce single-download burn limit and mark share COMPLETED")
    void testDownloadBurnAfterReadEnforcement() {
        String rawToken = "rawToken123";
        Long fileId = 20L;

        Share share = new Share();
        share.setId(3L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));
        share.setMaxDownloads(1); // burn after 1 download
        share.setDownloadCount(0);

        OtpVerification otp = new OtpVerification();
        otp.setVerifiedAt(Instant.now());

        SharedFile file = new SharedFile();
        file.setId(fileId);
        file.setStorageKey("3/burn.pdf");

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(3L)).thenReturn(Optional.of(otp));
        when(fileRepository.findByIdAndShareId(fileId, 3L)).thenReturn(Optional.of(file));
        when(storageService.loadAsResource("3/burn.pdf"))
                .thenReturn(new ByteArrayResource("secret".getBytes()));

        // First download succeeds
        Resource resource = downloadService.downloadFile(rawToken, fileId);
        assertNotNull(resource);
        assertEquals(1, share.getDownloadCount());
        assertEquals(ShareStatus.COMPLETED, share.getShareStatus());

        // Subsequent attempt must be rejected
        assertThrows(ShareExpiredException.class, () ->
                downloadService.downloadFile(rawToken, fileId)
        );
    }
}
