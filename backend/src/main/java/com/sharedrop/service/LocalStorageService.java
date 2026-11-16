package com.sharedrop.service;

import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.StorageException;
import com.sharedrop.util.FileValidator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem implementation of StorageService.
 * Streams files directly to disk to maintain a low memory footprint (no heap exhaustion).
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootLocation;
    private final FileValidator fileValidator;

    public LocalStorageService(
            @Value("${app.upload.storage-path:./uploads}") String storagePath,
            FileValidator fileValidator) {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        this.fileValidator = fileValidator;
    }

    @Override
    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
                log.info("Created storage root directory: {}", rootLocation);
            } else {
                log.info("Storage root directory verified at: {}", rootLocation);
            }
        } catch (IOException e) {
            throw new StorageException("Could not initialize local storage root directory: " + rootLocation, e);
        }
    }

    @Override
    public String store(MultipartFile file, String storageKey) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Failed to store empty file for key: " + storageKey);
        }
        try (InputStream inputStream = file.getInputStream()) {
            return store(inputStream, storageKey, file.getSize());
        } catch (IOException e) {
            throw new StorageException("Failed to read upload input stream for key: " + storageKey, e);
        }
    }

    @Override
    public String store(InputStream inputStream, String storageKey, long fileSize) {
        if (inputStream == null) {
            throw new StorageException("Cannot store null input stream for key: " + storageKey);
        }

        Path destinationFile = resolveSafePath(storageKey);

        try {
            Path parent = destinationFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Stream directly to file without buffering into memory
            long copiedBytes = Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored {} bytes at: {}", copiedBytes, destinationFile);
            return storageKey;
        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + storageKey, e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path file = resolveSafePath(storageKey);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File not found or unreadable: " + storageKey);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("Invalid file URI for key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path file = resolveSafePath(storageKey);
            if (Files.exists(file)) {
                Files.delete(file);
                log.debug("Deleted stored file: {}", storageKey);

                // Optionally clean up empty parent directories up to root
                Path parent = file.getParent();
                if (parent != null && !parent.equals(rootLocation) && isDirEmpty(parent)) {
                    Files.deleteIfExists(parent);
                }
            } else {
                log.debug("File to delete does not exist on disk: {}", storageKey);
            }
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path file = resolveSafePath(storageKey);
        return Files.exists(file);
    }

    /**
     * Resolves storage key safely against rootLocation to prevent directory traversal attacks.
     */
    private Path resolveSafePath(String storageKey) {
        Path resolved = rootLocation.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootLocation)) {
            log.warn("Directory traversal attempt detected: storageKey={}, root={}", storageKey, rootLocation);
            throw new StorageException("Cannot store or access file outside designated root directory");
        }
        return resolved;
    }

    private boolean isDirEmpty(Path path) {
        try (var entries = Files.list(path)) {
            return !entries.findFirst().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
