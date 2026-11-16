package com.sharedrop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized storage configuration. Provides storage path and limits
 * to StorageService implementations.
 */
@Configuration
public class StorageConfig {

    @Value("${app.upload.storage-path}")
    private String storagePath;

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    @Value("${app.upload.max-files-per-share}")
    private int maxFilesPerShare;

    @Value("${app.upload.max-text-size}")
    private long maxTextSize;

    public String getStoragePath() { return storagePath; }
    public long getMaxFileSize() { return maxFileSize; }
    public int getMaxFilesPerShare() { return maxFilesPerShare; }
    public long getMaxTextSize() { return maxTextSize; }
}
