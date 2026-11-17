package com.sharedrop.controller;

import com.sharedrop.security.RateLimitService;
import com.sharedrop.service.DownloadService;
import com.sharedrop.service.ExpirationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/shares")
@Tag(name = "File Download", description = "Endpoints for authorized streaming of protected files")
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService downloadService;
    private final ExpirationService expirationService;
    private final RateLimitService rateLimitService;
    private final int maxDownloadRate;

    public DownloadController(
            DownloadService downloadService,
            ExpirationService expirationService,
            RateLimitService rateLimitService,
            @Value("${app.security.rate-limit.max-requests-per-minute:60}") int maxDownloadRate) {
        this.downloadService = downloadService;
        this.expirationService = expirationService;
        this.rateLimitService = rateLimitService;
        this.maxDownloadRate = maxDownloadRate;
    }

    @GetMapping("/{token}/files/{fileId}/download")
    @Operation(summary = "Download file", description = "Authorizes and streams a file if OTP has been verified")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("token") String token,
            @PathVariable("fileId") Long fileId,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        rateLimitService.checkPerMinute("download:" + clientIp, maxDownloadRate);

        log.debug("Download requested for fileId={} in share token={} from IP: {}", fileId, token, clientIp);

        // Read metadata before downloadFile increments the count and may complete the session.
        String contentType = downloadService.getContentType(token, fileId);
        String filename = downloadService.getFilename(token, fileId);
        Resource resource = downloadService.downloadFile(token, fileId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        StreamingResponseBody stream = outputStream -> {
            try (InputStream inputStream = resource.getInputStream()) {
                inputStream.transferTo(outputStream);
                outputStream.flush();
            } finally {
                // If this was the final permitted download, delete the session only after streaming.
                expirationService.cleanupCompletedSession(token);
            }
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(stream);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
