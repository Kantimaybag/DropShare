package com.sharedrop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedrop.dto.request.CreateTextShareRequest;
import com.sharedrop.dto.request.VerifyOtpRequest;
import com.sharedrop.entity.OtpVerification;
import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import com.sharedrop.exception.GlobalExceptionHandler;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.OtpRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.security.RateLimitService;
import com.sharedrop.service.*;
import com.sharedrop.util.FileValidator;
import com.sharedrop.util.HashUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShareControllerIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private StorageService storageService;

    private ShareService shareService;
    private OtpService otpService;
    private DownloadService downloadService;
    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        fileValidator = new FileValidator("jpg,png,pdf,txt", 50 * 1024 * 1024, 10, 1024 * 1024);
        RateLimitService rateLimitService = new RateLimitService();

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

        otpService = new OtpService(otpRepository, shareRepository, 5, 15);
        downloadService = new DownloadService(shareRepository, fileRepository, otpRepository, storageService);

        ShareController shareController = new ShareController(shareService, rateLimitService, 20);
        OtpController otpController = new OtpController(otpService, rateLimitService, 20);
        DownloadController downloadController = new DownloadController(downloadService, rateLimitService, 60);
        UploadController uploadController = new UploadController(fileValidator, rateLimitService);

        this.mockMvc = MockMvcBuilders.standaloneSetup(shareController, otpController, downloadController, uploadController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/shares/file - should create file share and return 201 Created")
    void testCreateFileShareEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "test.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(10L);
            return s;
        });

        when(fileRepository.save(any(SharedFile.class))).thenAnswer(invocation -> {
            SharedFile f = invocation.getArgument(0);
            f.setId(100L);
            return f;
        });

        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(multipart("/api/v1/shares/file")
                        .file(file)
                        .param("expiration", "24h"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareToken").isNotEmpty())
                .andExpect(jsonPath("$.data.otp").isNotEmpty())
                .andExpect(jsonPath("$.data.files[0].originalFilename").value("test.png"));
    }

    @Test
    @DisplayName("POST /api/v1/shares/text - should create text share and return 201 Created")
    void testCreateTextShareEndpoint() throws Exception {
        CreateTextShareRequest request = new CreateTextShareRequest();
        request.setTextContent("Confidential text payload");
        request.setExpiration("1h");

        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(20L);
            return s;
        });

        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/shares/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareToken").isNotEmpty())
                .andExpect(jsonPath("$.data.otp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/shares/{token}/verify - should verify OTP and unlock content")
    void testVerifyOtpEndpoint() throws Exception {
        String rawToken = "sampleToken123";
        String rawOtp = "123456";

        Share share = new Share();
        share.setId(30L);
        share.setShareType(ShareType.FILE);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        OtpVerification otp = new OtpVerification();
        otp.setShare(share);
        otp.setOtpHash(HashUtil.bcryptHash(rawOtp));
        otp.setExpiresAt(Instant.now().plusSeconds(1800));
        otp.setFailedAttempts(0);

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(30L)).thenReturn(Optional.of(otp));

        VerifyOtpRequest request = new VerifyOtpRequest(rawOtp);

        mockMvc.perform(post("/api/v1/shares/" + rawToken + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareType").value("FILE"));
    }

    @Test
    @DisplayName("GET /api/v1/shares/{token}/files/{fileId}/download - should stream file when authorized")
    void testDownloadFileEndpoint() throws Exception {
        String rawToken = "sampleToken123";
        Long fileId = 50L;

        Share share = new Share();
        share.setId(40L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));
        share.setMaxDownloads(100);
        share.setDownloadCount(0);

        OtpVerification otp = new OtpVerification();
        otp.setVerifiedAt(Instant.now()); // Verified!

        SharedFile file = new SharedFile();
        file.setId(fileId);
        file.setOriginalFilename("test.pdf");
        file.setStorageKey("40/test.pdf");
        file.setContentType("application/pdf");

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));
        when(otpRepository.findByShareId(40L)).thenReturn(Optional.of(otp));
        when(fileRepository.findByIdAndShareId(fileId, 40L)).thenReturn(Optional.of(file));
        when(storageService.loadAsResource("40/test.pdf")).thenReturn(new ByteArrayResource("sample content".getBytes()));

        mockMvc.perform(get("/api/v1/shares/" + rawToken + "/files/" + fileId + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("test.pdf")))
                .andExpect(content().string("sample content"));
    }

    @Test
    @DisplayName("GET /api/v1/shares/{token}/status - should return share status")
    void testGetShareStatusEndpoint() throws Exception {
        String rawToken = "sampleToken123";

        Share share = new Share();
        share.setId(50L);
        share.setShareType(ShareType.FILE);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setCreatedAt(Instant.now().minusSeconds(100));
        share.setExpiresAt(Instant.now().plusSeconds(3600));
        share.setDownloadCount(0);
        share.setMaxDownloads(100);

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));

        mockMvc.perform(get("/api/v1/shares/" + rawToken + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.shareType").value("FILE"));
    }

    @Test
    @DisplayName("POST /api/v1/shares/{token}/revoke - should revoke share")
    void testRevokeShareEndpoint() throws Exception {
        String rawToken = "sampleToken123";

        Share share = new Share();
        share.setId(60L);
        share.setShareStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        when(shareRepository.findByPublicToken(anyString())).thenReturn(Optional.of(share));

        mockMvc.perform(post("/api/v1/shares/" + rawToken + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Share revoked successfully"));
    }

    @Test
    @DisplayName("GET /api/v1/uploads/limits - should return file upload constraints")
    void testGetUploadLimitsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxFileSizeBytes").isNumber());
    }
}
