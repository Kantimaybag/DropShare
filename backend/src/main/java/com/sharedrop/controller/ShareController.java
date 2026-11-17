package com.sharedrop.controller;

import com.sharedrop.dto.request.CreateFileShareRequest;
import com.sharedrop.dto.request.CreateTextShareRequest;
import com.sharedrop.dto.response.ApiResponse;
import com.sharedrop.dto.response.CreateShareResponse;
import com.sharedrop.dto.response.ShareStatusResponse;
import com.sharedrop.dto.response.ShareManagementResponse;
import com.sharedrop.dto.response.AuditEventResponse;
import com.sharedrop.service.AuditLogService;
import com.sharedrop.security.RateLimitService;
import com.sharedrop.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shares")
@Tag(name = "Share Management", description = "Endpoints for creating, querying, and revoking file and text shares")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final int maxUploadRate;

    public ShareController(
            ShareService shareService,
            RateLimitService rateLimitService,
            AuditLogService auditLogService,
            @Value("${app.security.rate-limit.max-upload-requests-per-minute:10}") int maxUploadRate) {
        this.shareService = shareService;
        this.rateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
        this.maxUploadRate = maxUploadRate;
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create file share", description = "Uploads one or multiple files and creates an OTP-protected share link")
    public ResponseEntity<ApiResponse<CreateShareResponse>> createFileShare(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("expiration") String expiration,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        rateLimitService.checkPerMinute("upload:" + clientIp, maxUploadRate);

        log.debug("Creating file share with {} files from IP: {}", files.size(), clientIp);

        CreateFileShareRequest shareRequest = new CreateFileShareRequest();
        shareRequest.setExpiration(expiration);
        shareRequest.setPassword(password);

        CreateShareResponse response = shareService.createFileShare(files, shareRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File share created successfully", response));
    }

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create text share", description = "Stores sensitive text or code snippets with OTP-protected access")
    public ResponseEntity<ApiResponse<CreateShareResponse>> createTextShare(
            @Valid @RequestBody CreateTextShareRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        rateLimitService.checkPerMinute("text:" + clientIp, maxUploadRate);

        log.debug("Creating text share from IP: {}", clientIp);

        CreateShareResponse response = shareService.createTextShare(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Text share created successfully", response));
    }

    @PostMapping(value = "/{token}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add files to an existing session")
    public ResponseEntity<ApiResponse<Void>> addFiles(
            @PathVariable String token,
            @RequestHeader("X-Share-Owner") String ownerSecret,
            @RequestParam("files") List<MultipartFile> files) {
        shareService.addFilesToSession(token, ownerSecret, files);
        return ResponseEntity.ok(ApiResponse.success("Files added to the existing session"));
    }

    @PostMapping(value = "/{token}/texts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a text entry to an existing session")
    public ResponseEntity<ApiResponse<Void>> addText(
            @PathVariable String token,
            @RequestHeader("X-Share-Owner") String ownerSecret,
            @Valid @RequestBody CreateTextShareRequest request) {
        shareService.addTextToSession(token, ownerSecret, request);
        return ResponseEntity.ok(ApiResponse.success("Text added to the existing session"));
    }

    @GetMapping("/{token}/manage")
    @Operation(summary = "Get sender view of a sharing session")
    public ResponseEntity<ApiResponse<ShareManagementResponse>> manage(
            @PathVariable String token,
            @RequestHeader("X-Share-Owner") String ownerSecret) {
        return ResponseEntity.ok(ApiResponse.success("Session details retrieved",
                shareService.getManagementDetails(token, ownerSecret)));
    }

    @GetMapping("/{token}/audit")
    @Operation(summary = "Get the session audit log")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> audit(
            @PathVariable String token,
            @RequestHeader("X-Share-Owner") String ownerSecret) {
        return ResponseEntity.ok(ApiResponse.success("Audit log retrieved",
                auditLogService.getForOwner(token, ownerSecret)));
    }

    @GetMapping("/{token}/status")
    @Operation(summary = "Get share status", description = "Returns active/expired/revoked status without exposing content")
    public ResponseEntity<ApiResponse<ShareStatusResponse>> getShareStatus(
            @PathVariable("token") String token,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        rateLimitService.checkPerMinute("status:" + clientIp, 60);

        ShareStatusResponse status = shareService.getShareStatus(token);
        return ResponseEntity.ok(ApiResponse.success("Share status retrieved", status));
    }

    @PostMapping("/{token}/revoke")
    @Operation(summary = "Revoke share", description = "Instantly revokes a share and purges its stored files")
    public ResponseEntity<ApiResponse<Void>> revokeShare(
            @PathVariable("token") String token,
            @RequestHeader("X-Share-Owner") String ownerSecret,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        rateLimitService.checkPerMinute("revoke:" + clientIp, 20);

        shareService.revokeShare(token, ownerSecret);
        return ResponseEntity.ok(ApiResponse.success("Share revoked successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
