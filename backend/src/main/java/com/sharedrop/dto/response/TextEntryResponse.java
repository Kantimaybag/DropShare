package com.sharedrop.dto.response;

public class TextEntryResponse {
    private Long id;
    private String title;
    private String content;
    private String format;
    private String createdAt;

    public TextEntryResponse() {}
    public TextEntryResponse(Long id, String title, String content, String format, String createdAt) {
        this.id = id; this.title = title; this.content = content; this.format = format; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
