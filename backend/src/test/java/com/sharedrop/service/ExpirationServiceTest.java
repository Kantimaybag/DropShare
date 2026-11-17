package com.sharedrop.service;

import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.ShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpirationServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditLogService auditLogService;

    private ExpirationService expirationService;

    @BeforeEach
    void setUp() {
        expirationService = new ExpirationService(shareRepository, fileRepository, storageService, auditLogService);
    }

    @Test
    @DisplayName("Should detect and transition expired active shares to EXPIRED status")
    void testExpireShares() {
        Share s1 = new Share();
        s1.setId(1L);
        s1.setShareStatus(ShareStatus.ACTIVE);
        s1.setExpiresAt(Instant.now().minusSeconds(100));

        Share s2 = new Share();
        s2.setId(2L);
        s2.setShareStatus(ShareStatus.ACTIVE);
        s2.setExpiresAt(Instant.now().minusSeconds(200));

        when(shareRepository.findExpiredActiveShares(any(Instant.class))).thenReturn(List.of(s1, s2));

        int expiredCount = expirationService.expireShares();

        assertEquals(2, expiredCount);
        assertEquals(ShareStatus.EXPIRED, s1.getShareStatus());
        assertEquals(ShareStatus.EXPIRED, s2.getShareStatus());
        verify(shareRepository, times(2)).save(any(Share.class));
    }

    @Test
    @DisplayName("Should purge files from storage and delete database records for inactive shares")
    void testCleanupExpiredFiles() {
        Share expiredShare = new Share();
        expiredShare.setId(10L);
        expiredShare.setShareStatus(ShareStatus.EXPIRED);

        SharedFile f1 = new SharedFile();
        f1.setStorageKey("10/file1.png");
        SharedFile f2 = new SharedFile();
        f2.setStorageKey("10/file2.pdf");
        expiredShare.setFiles(List.of(f1, f2));

        when(shareRepository.findByShareStatusIn(any())).thenReturn(List.of(expiredShare));

        int cleaned = expirationService.cleanupExpiredFiles(100);

        assertEquals(1, cleaned);
        verify(storageService, times(1)).delete("10/file1.png");
        verify(storageService, times(1)).delete("10/file2.pdf");
        verify(shareRepository, times(1)).delete(expiredShare);
    }
}
