package com.sharedrop.dto.response;

public class AuditEventResponse {
    private String eventType;
    private String description;
    private String createdAt;

    public AuditEventResponse() {}
    public AuditEventResponse(String eventType, String description, String createdAt) {
        this.eventType = eventType; this.description = description; this.createdAt = createdAt;
    }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
