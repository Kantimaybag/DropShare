package com.sharedrop.controller;

import com.sharedrop.dto.request.VerifyOtpRequest;
import com.sharedrop.dto.response.ApiResponse;
import com.sharedrop.dto.response.VerifyOtpResponse;
import com.sharedrop.dto.response.ResendOtpResponse;
import com.sharedrop.security.RateLimitService;
import com.sharedrop.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shares")
@Tag(name = "OTP Verification", description = "Endpoints for verifying recipient OTP access to shared files or text")
public class OtpController {

    private static final Logger log = LoggerFactory.getLogger(OtpController.class);

    private final OtpService otpService;
    private final RateLimitService rateLimitService;
    private final int maxVerifyRate;
    private final int maxResendRate;

    public OtpController(
            OtpService otpService,
            RateLimitService rateLimitService,
            @Value("${app.security.rate-limit.max-verify-requests-per-minute:10}") int maxVerifyRate,
            @Value("${app.security.rate-limit.max-resend-requests-per-minute:3}") int maxResendRate) {
        this.otpService = otpService;
        this.rateLimitService = rateLimitService;
        this.maxVerifyRate = maxVerifyRate;
        this.maxResendRate = maxResendRate;
    }

    @PostMapping("/{token}/resend-otp")
    @Operation(summary = "Resend expired OTP", description = "Creates a replacement OTP after expiry. Requires the sender-only resend secret.")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(
            @PathVariable("token") String token,
            @RequestHeader("X-Resend-Secret") String resendSecret,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        rateLimitService.checkPerMinute("resend-otp:" + clientIp, maxResendRate);

        ResendOtpResponse response = otpService.resendOtp(token, resendSecret);
        return ResponseEntity.ok(ApiResponse.success("A new OTP was generated successfully", response));
    }

    @PostMapping("/{token}/verify")
    @Operation(summary = "Verify OTP", description = "Verifies the 6-digit OTP and unlocks the shared files or text content")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
            @PathVariable("token") String token,
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        rateLimitService.checkPerMinute("verify:" + clientIp, maxVerifyRate);

        log.debug("OTP verification requested for token from IP: {}", clientIp);

        VerifyOtpResponse response = otpService.verifyOtp(token, request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
