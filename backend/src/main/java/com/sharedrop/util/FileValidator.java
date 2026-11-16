package com.sharedrop.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates uploaded files for extension, size, and path safety.
 */
@Component
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private final Set<String> allowedExtensions;
    private final long maxFileSize;
    private final int maxFilesPerShare;
    private final long maxTextSize;

    public FileValidator(
            @Value("${app.upload.allowed-extensions}") String extensions,
            @Value("${app.upload.max-file-size}") long maxFileSize,
            @Value("${app.upload.max-files-per-share}") int maxFilesPerShare,
            @Value("${app.upload.max-text-size}") long maxTextSize) {
        this.allowedExtensions = Stream.of(extensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        this.maxFileSize = maxFileSize;
        this.maxFilesPerShare = maxFilesPerShare;
        this.maxTextSize = maxTextSize;
    }

    /** Checks if the filename extension is in the allow-list. */
    public boolean isAllowedExtension(String filename) {
        if (filename == null || filename.isBlank()) return false;
        String ext = getExtension(filename).toLowerCase();
        if (ext.isEmpty()) return true; // files without extension are allowed
        return allowedExtensions.contains(ext);
    }

    /** Checks if the file size is within the configured limit. */
    public boolean isAllowedSize(long size) {
        return size > 0 && size <= maxFileSize;
    }

    /** Checks if the number of files is within the per-share limit. */
    public boolean isAllowedFileCount(int count) {
        return count > 0 && count <= maxFilesPerShare;
    }

    /** Checks if the text content size is within limits. */
    public boolean isAllowedTextSize(long size) {
        return size > 0 && size <= maxTextSize;
    }

    /**
     * Sanitizes filename: extracts only the basename, removes path separators,
     * null bytes, and dangerous characters to prevent path traversal.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";

        // Extract filename portion only if paths are passed
        String basename = filename;
        int lastUnix = basename.lastIndexOf('/');
        int lastWin = basename.lastIndexOf('\\');
        int lastSep = Math.max(lastUnix, lastWin);
        if (lastSep >= 0 && lastSep < basename.length() - 1) {
            basename = basename.substring(lastSep + 1);
        }

        // Remove path separators and null bytes
        String safe = basename.replace("\0", "")
                .replace("/", "")
                .replace("\\", "")
                .replace("..", "")
                .trim();

        // Ensure the name is not empty after sanitization
        if (safe.isBlank()) return "unnamed";

        // Truncate to reasonable length
        if (safe.length() > 255) {
            String ext = getExtension(safe);
            safe = safe.substring(0, 250 - ext.length()) + (ext.isEmpty() ? "" : "." + ext);
        }
        return safe;
    }

    /** Extracts lowercase file extension without the dot. */
    public String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    /**
     * Validates that a storage key does not escape the base storage directory.
     */
    public boolean isSafeStoragePath(String storageKey, String basePath) {
        try {
            Path resolved = Path.of(basePath).resolve(storageKey).normalize();
            return resolved.startsWith(Path.of(basePath).normalize());
        } catch (Exception e) {
            log.warn("Unsafe storage path detected: {}", storageKey);
            return false;
        }
    }

    public long getMaxFileSize() { return maxFileSize; }
    public int getMaxFilesPerShare() { return maxFilesPerShare; }
    public long getMaxTextSize() { return maxTextSize; }
}
