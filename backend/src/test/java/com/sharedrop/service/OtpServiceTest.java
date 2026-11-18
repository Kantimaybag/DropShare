package com.sharedrop.service;

import com.sharedrop.dto.request.VerifyOtpRequest;
import com.sharedrop.dto.response.VerifyOtpResponse;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.entity.SharedTextEntry;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ShareExpiredException;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.repository.SharedTextRepository;
import com.sharedrop.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private final AesGcmEncryptionService encryptionService = new AesGcmEncryptionService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private SharedTextRepository sharedTextRepository;

    @Mock
    private AuditLogService auditLogService;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpRepository, shareRepository, sharedTextRepository, auditLogService, encryptionService, 5, 15, 10);
    }

    @Test
    @DisplayName("Should successfully verify correct OTP and return file list")
    void testVerifyOtpSuccess() {
        String rawToken = "myToken1234567890";
        String correctOtp = "654321";

        Share share = new Share();
        share.setId(1L);
        share.setShareType(ShareType.FILE);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        SharedFile file = new SharedFile();
        file.setId(10L);
        file.setOriginalFilename("test.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(2048L);
        share.setFiles(List.of(file));

        OtpVerification otp = new OtpVerification();
        otp.setId(5L);
        otp.setShare(share);
        otp.setOtpHash(HashUtil.bcryptHash(correctOtp));
        otp.setExpiresAt(Instant.now().plusSeconds(1800));
        otp.setFailedAttempts(0);

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(1L)).thenReturn(Optional.of(otp));
        SharedTextEntry encryptedText = new SharedTextEntry();
        encryptedText.setId(99L);
        encryptedText.setTitle("Protected note");
        encryptedText.setContent(encryptionService.encryptText("Hello from encrypted storage"));
        encryptedText.setFormat("text");
        encryptedText.setCreatedAt(Instant.now());
        when(sharedTextRepository.findByShareIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(encryptedText));

        VerifyOtpRequest request = new VerifyOtpRequest(correctOtp);
        VerifyOtpResponse response = otpService.verifyOtp(rawToken, request);

        assertNotNull(response);
        assertEquals(ShareType.MIXED.name(), response.getShareType());
        assertEquals(1, response.getFiles().size());
        assertEquals("test.pdf", response.getFiles().get(0).getOriginalFilename());
        assertEquals(1, response.getTextEntries().size());
        assertEquals("Hello from encrypted storage", response.getTextEntries().get(0).getContent());

        assertNotNull(otp.getVerifiedAt());
        assertEquals(0, otp.getFailedAttempts());
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("Should increment failed attempts on incorrect OTP and report remaining")
    void testVerifyOtpIncorrect() {
        String rawToken = "myToken1234567890";
        String correctOtp = "111111";
        String wrongOtp = "999999";

        Share share = new Share();
        share.setId(2L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        OtpVerification otp = new OtpVerification();
        otp.setShare(share);
        otp.setOtpHash(HashUtil.bcryptHash(correctOtp));
        otp.setExpiresAt(Instant.now().plusSeconds(1800));
        otp.setFailedAttempts(0);

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(2L)).thenReturn(Optional.of(otp));

        VerifyOtpRequest request = new VerifyOtpRequest(wrongOtp);

        InvalidOtpException ex = assertThrows(InvalidOtpException.class, () ->
                otpService.verifyOtp(rawToken, request)
        );

        assertEquals(4, ex.getRemainingAttempts());
        assertEquals(1, otp.getFailedAttempts());
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("Should lock account when max attempts are reached")
    void testVerifyOtpLockout() {
        String rawToken = "myToken1234567890";
        String correctOtp = "111111";

        Share share = new Share();
        share.setId(3L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        OtpVerification otp = new OtpVerification();
        otp.setShare(share);
        otp.setOtpHash(HashUtil.bcryptHash(correctOtp));
        otp.setExpiresAt(Instant.now().plusSeconds(1800));
        otp.setFailedAttempts(4); // 4 already failed, next one reaches max 5

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(3L)).thenReturn(Optional.of(otp));

        VerifyOtpRequest request = new VerifyOtpRequest("wrongOne");

        InvalidOtpException ex = assertThrows(InvalidOtpException.class, () ->
                otpService.verifyOtp(rawToken, request)
        );

        assertTrue(ex.getMessage().contains("locked"));
        assertNotNull(otp.getLockedUntil());
        assertTrue(otp.isLocked());
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("Should reject verification if OTP has expired")
    void testVerifyOtpExpired() {
        String rawToken = "myToken1234567890";

        Share share = new Share();
        share.setId(4L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        OtpVerification otp = new OtpVerification();
        otp.setShare(share);
        otp.setOtpHash(HashUtil.bcryptHash("123456"));
        otp.setExpiresAt(Instant.now().minusSeconds(60)); // expired 1 min ago

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(4L)).thenReturn(Optional.of(otp));

        assertThrows(InvalidOtpException.class, () ->
                otpService.verifyOtp(rawToken, new VerifyOtpRequest("123456"))
        );
    }

    @Test
    @DisplayName("Should reject verification if share itself has expired")
    void testVerifyOtpShareExpired() {
        String rawToken = "myToken1234567890";

        Share share = new Share();
        share.setId(5L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().minusSeconds(3600)); // share expired

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));

        assertThrows(ShareExpiredException.class, () ->
                otpService.verifyOtp(rawToken, new VerifyOtpRequest("123456"))
        );
    }
}
