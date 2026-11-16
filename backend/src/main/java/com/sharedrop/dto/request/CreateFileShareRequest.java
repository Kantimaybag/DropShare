package com.sharedrop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateFileShareRequest {

    @NotBlank(message = "Expiration is required")
    @Pattern(regexp = "^(10m|1h|24h|7d|burn)$", message = "Invalid expiration value")
    private String expiration;

    private String password;

    // ── Getters & Setters ────────────────────────────────────────

    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
