package com.sharedrop.service;

import com.sharedrop.dto.request.CreateFileShareRequest;
import com.sharedrop.dto.request.CreateTextShareRequest;
import com.sharedrop.dto.response.CreateShareResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.util.FileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private StorageService storageService;

    private FileValidator fileValidator;
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        fileValidator = new FileValidator("jpg,png,pdf,txt", 50 * 1024 * 1024, 10, 1024 * 1024);
        shareService = new ShareService(
                shareRepository,
                fileRepository,
                otpRepository,
                storageService,
                fileValidator,
                "http://localhost:8080",
                30,
                100
        );
    }

    @Test
    @DisplayName("Should successfully create file share and return token and OTP")
    void testCreateFileShareSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "test-document.pdf",
                "application/pdf",
                "sample content".getBytes()
        );

        CreateFileShareRequest request = new CreateFileShareRequest();
        request.setExpiration("24h");

        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(101L);
            return s;
        });

        when(fileRepository.save(any(SharedFile.class))).thenAnswer(invocation -> {
            SharedFile f = invocation.getArgument(0);
            f.setId(201L);
            return f;
        });

        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateShareResponse response = shareService.createFileShare(List.of(file), request);

        assertNotNull(response);
        assertNotNull(response.getShareToken());
        assertTrue(response.getShareUrl().contains("/d/"));
        assertNotNull(response.getOtp());
        assertEquals(6, response.getOtp().length());
        assertEquals(ShareType.FILE.name(), response.getShareType());
        assertEquals(1, response.getFiles().size());
        assertEquals("test-document.pdf", response.getFiles().get(0).getOriginalFilename());

        verify(storageService, times(1)).store(any(), anyString());
        verify(shareRepository, times(1)).save(any(Share.class));
        verify(fileRepository, times(1)).save(any(SharedFile.class));
        verify(otpRepository, times(1)).save(any(OtpVerification.class));
    }

    @Test
    @DisplayName("Should configure single download max for burn-after-read expiration")
    void testCreateFileShareBurnAfterRead() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "secret.pdf",
                "application/pdf",
                "secret content".getBytes()
        );

        CreateFileShareRequest request = new CreateFileShareRequest();
        request.setExpiration("burn");

        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(102L);
            assertEquals(1, s.getMaxDownloads(), "Burn after read must set maxDownloads to 1");
            return s;
        });

        when(fileRepository.save(any(SharedFile.class))).thenAnswer(invocation -> {
            SharedFile f = invocation.getArgument(0);
            f.setId(202L);
            return f;
        });

        shareService.createFileShare(List.of(file), request);
        verify(shareRepository).save(any(Share.class));
    }

    @Test
    @DisplayName("Should successfully create text share")
    void testCreateTextShareSuccess() {
        CreateTextShareRequest request = new CreateTextShareRequest();
        request.setTextContent("Top secret API key: sk_live_xyz123");
        request.setExpiration("1h");

        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(103L);
            return s;
        });

        CreateShareResponse response = shareService.createTextShare(request);

        assertNotNull(response);
        assertNotNull(response.getShareToken());
        assertEquals(ShareType.TEXT.name(), response.getShareType());
        assertNotNull(response.getOtp());
        verify(shareRepository, times(1)).save(any(Share.class));
        verify(otpRepository, times(1)).save(any(OtpVerification.class));
    }

    @Test
    @DisplayName("Should throw exception if files list is empty")
    void testCreateFileShareEmptyList() {
        CreateFileShareRequest request = new CreateFileShareRequest();
        request.setExpiration("24h");

        assertThrows(IllegalArgumentException.class, () ->
                shareService.createFileShare(List.of(), request)
        );
    }

    @Test
    @DisplayName("Should reject disallowed file extension")
    void testCreateFileShareDisallowedExtension() {
        MockMultipartFile badFile = new MockMultipartFile(
                "files",
                "exploit.exe",
                "application/x-msdownload",
                "evil payload".getBytes()
        );

        CreateFileShareRequest request = new CreateFileShareRequest();
        request.setExpiration("24h");

        assertThrows(IllegalArgumentException.class, () ->
                shareService.createFileShare(List.of(badFile), request)
        );
    }

    @Test
    @DisplayName("Should throw ShareExpiredException if share has expired")
    void testFindByTokenExpired() {
        String rawToken = "sampleToken12345678901234567890";
        Share expiredShare = new Share();
        expiredShare.setId(104L);
        expiredShare.setShareStatus(ShareStatus.ACTIVE);
        expiredShare.setExpiresAt(Instant.now().minusSeconds(3600)); // expired 1 hour ago

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(expiredShare));

        assertThrows(ShareExpiredException.class, () ->
                shareService.findByToken(rawToken)
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for unknown token")
    void testFindByTokenNotFound() {
        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                shareService.findByToken("nonexistentToken")
        );
    }

    @Test
    @DisplayName("Should revoke share and delete files from storage")
    void testRevokeShare() {
        String rawToken = "validTokenForRevocation";
        Share share = new Share();
        share.setId(105L);
        share.setShareStatus(ShareStatus.ACTIVE);

        SharedFile file = new SharedFile();
        file.setStorageKey("105/some-key.pdf");
        share.setFiles(List.of(file));

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));

        shareService.revokeShare(rawToken);

        assertEquals(ShareStatus.REVOKED, share.getShareStatus());
        verify(storageService, times(1)).delete("105/some-key.pdf");
        verify(shareRepository, times(1)).save(share);
    }
}
