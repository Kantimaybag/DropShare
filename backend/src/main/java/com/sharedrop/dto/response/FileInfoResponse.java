package com.sharedrop.dto.response;

public class FileInfoResponse {

    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String formattedSize;

    public FileInfoResponse() {}

    public FileInfoResponse(Long id, String originalFilename, String contentType, Long fileSize) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.formattedSize = formatBytes(fileSize);
    }

    // ── Getters & Setters ────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFormattedSize() { return formattedSize; }
    public void setFormattedSize(String formattedSize) { this.formattedSize = formattedSize; }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(bytes) / Math.log(1024));
        i = Math.min(i, units.length - 1);
        double value = bytes / Math.pow(1024, i);
        return String.format("%.1f %s", value, units[i]);
    }
}
