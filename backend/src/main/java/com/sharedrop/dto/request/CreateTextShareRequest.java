package com.sharedrop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateTextShareRequest {

    @NotBlank(message = "Text content is required")
    private String textContent;

    @NotBlank(message = "Expiration is required")
    @Pattern(regexp = "^(10m|1h|24h|7d|burn)$", message = "Invalid expiration value")
    private String expiration;

    private String snippetFormat;

    private String password;

    // ── Getters & Setters ────────────────────────────────────────

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }

    public String getSnippetFormat() { return snippetFormat; }
    public void setSnippetFormat(String snippetFormat) { this.snippetFormat = snippetFormat; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
