package com.sharedrop.dto.response;

import java.util.List;

public class CreateShareResponse {

    private String shareToken;
    private String shareUrl;
    private String otp;
    private String resendSecret;
    private String expiresAt;
    private String shareType;
    private List<FileInfoResponse> files;

    // ── Getters & Setters ────────────────────────────────────────

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public String getShareUrl() { return shareUrl; }
    public void setShareUrl(String shareUrl) { this.shareUrl = shareUrl; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getResendSecret() { return resendSecret; }
    public void setResendSecret(String resendSecret) { this.resendSecret = resendSecret; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getShareType() { return shareType; }
    public void setShareType(String shareType) { this.shareType = shareType; }

    public List<FileInfoResponse> getFiles() { return files; }
    public void setFiles(List<FileInfoResponse> files) { this.files = files; }
}
