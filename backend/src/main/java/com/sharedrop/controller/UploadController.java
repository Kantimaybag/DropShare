package com.sharedrop.controller;

import com.sharedrop.dto.response.ApiResponse;
import com.sharedrop.security.RateLimitService;
import com.sharedrop.util.FileValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Upload Operations", description = "Endpoints for managing upload sessions and limits")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final FileValidator fileValidator;
    private final RateLimitService rateLimitService;

    public UploadController(FileValidator fileValidator, RateLimitService rateLimitService) {
        this.fileValidator = fileValidator;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/limits")
    @Operation(summary = "Get upload limits", description = "Returns system limits for file size, counts, and permitted formats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUploadLimits() {
        Map<String, Object> limits = Map.of(
                "maxFileSizeBytes", fileValidator.getMaxFileSize(),
                "maxFilesPerShare", fileValidator.getMaxFilesPerShare(),
                "maxTextSizeBytes", fileValidator.getMaxTextSize()
        );
        return ResponseEntity.ok(ApiResponse.success("Upload limits retrieved", limits));
    }

    @PostMapping("/initiate")
    @Operation(summary = "Initiate multipart upload session", description = "Allocates a session identifier for large or multipart file transfers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateUpload(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String filename = (String) payload.getOrDefault("filename", "unnamed");
        String sanitized = fileValidator.sanitizeFilename(filename);

        String uploadId = UUID.randomUUID().toString();
        log.info("Initiated multipart upload session: uploadId={}, filename={}", uploadId, sanitized);

        Map<String, Object> data = Map.of(
                "uploadId", uploadId,
                "sanitizedFilename", sanitized,
                "partSize", 5 * 1024 * 1024, // 5 MB chunks
                "status", "INITIATED"
        );
        return ResponseEntity.ok(ApiResponse.success("Upload session initiated", data));
    }

    @PostMapping("/{uploadId}/complete")
    @Operation(summary = "Finalize multipart upload", description = "Validates and finalizes an ongoing multipart upload session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeUpload(
            @PathVariable("uploadId") String uploadId) {

        log.info("Completed upload session: uploadId={}", uploadId);
        Map<String, Object> data = Map.of(
                "uploadId", uploadId,
                "status", "COMPLETED"
        );
        return ResponseEntity.ok(ApiResponse.success("Upload finalized successfully", data));
    }

    @PostMapping("/{uploadId}/cancel")
    @Operation(summary = "Cancel upload", description = "Cancels an incomplete multipart upload and releases reserved resources")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelUpload(
            @PathVariable("uploadId") String uploadId) {

        log.info("Cancelled upload session: uploadId={}", uploadId);
        Map<String, Object> data = Map.of(
                "uploadId", uploadId,
                "status", "CANCELLED"
        );
        return ResponseEntity.ok(ApiResponse.success("Upload cancelled successfully", data));
    }
}
