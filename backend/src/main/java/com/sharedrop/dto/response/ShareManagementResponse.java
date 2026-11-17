package com.sharedrop.dto.response;

import java.util.List;

public class ShareManagementResponse {
    private String status;
    private String createdAt;
    private String expiresAt;
    private int downloadCount;
    private int maxDownloads;
    private List<FileInfoResponse> files;
    private List<TextEntryResponse> textEntries;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public int getMaxDownloads() { return maxDownloads; }
    public void setMaxDownloads(int maxDownloads) { this.maxDownloads = maxDownloads; }
    public List<FileInfoResponse> getFiles() { return files; }
    public void setFiles(List<FileInfoResponse> files) { this.files = files; }
    public List<TextEntryResponse> getTextEntries() { return textEntries; }
    public void setTextEntries(List<TextEntryResponse> textEntries) { this.textEntries = textEntries; }
}
