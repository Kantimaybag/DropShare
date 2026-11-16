package com.sharedrop.dto.response;

import java.util.List;

public class VerifyOtpResponse {

    private String shareType;
    private List<FileInfoResponse> files;
    private String textContent;

    // ── Getters & Setters ────────────────────────────────────────

    public String getShareType() { return shareType; }
    public void setShareType(String shareType) { this.shareType = shareType; }

    public List<FileInfoResponse> getFiles() { return files; }
    public void setFiles(List<FileInfoResponse> files) { this.files = files; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
}
