package com.sharedrop.dto.response;

public class ResendOtpResponse {
    private String otp;
    private String resendSecret;
    private String expiresAt;

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getResendSecret() { return resendSecret; }
    public void setResendSecret(String resendSecret) { this.resendSecret = resendSecret; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
