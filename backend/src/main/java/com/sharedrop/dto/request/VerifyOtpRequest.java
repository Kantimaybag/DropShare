package com.sharedrop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyOtpRequest {

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    private String otp;

    private String password;

    public VerifyOtpRequest() {}

    public VerifyOtpRequest(String otp) {
        this.otp = otp;
    }

    public VerifyOtpRequest(String otp, String password) {
        this.otp = otp;
        this.password = password;
    }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
