package com.sharedrop.dto.response;

public class ShareStatusResponse {

    private String status;
    private String shareType;
    private String expiresAt;
    private String createdAt;
    private Integer downloadCount;
    private Integer maxDownloads;

    // ── Getters & Setters ────────────────────────────────────────

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getShareType() { return shareType; }
    public void setShareType(String shareType) { this.shareType = shareType; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }

    public Integer getMaxDownloads() { return maxDownloads; }
    public void setMaxDownloads(Integer maxDownloads) { this.maxDownloads = maxDownloads; }
}
