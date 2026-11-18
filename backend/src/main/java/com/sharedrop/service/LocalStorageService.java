package com.sharedrop.service;

import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** Local filesystem storage. All file bytes are encrypted with AES-256-GCM at rest. */
@Service
public class LocalStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path rootLocation;
    private final AesGcmEncryptionService encryptionService;

    public LocalStorageService(
            @Value("${app.upload.storage-path:./uploads}") String storagePath,
            AesGcmEncryptionService encryptionService) {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        this.encryptionService = encryptionService;
    }

    @Override
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Encrypted storage root ready at: {}", rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize encrypted storage directory: " + rootLocation, e);
        }
    }

    @Override
    public String store(MultipartFile file, String storageKey) {
        if (file == null || file.isEmpty()) throw new StorageException("Cannot store an empty file.");
        try (InputStream input = file.getInputStream()) {
            return store(input, storageKey, file.getSize());
        } catch (IOException e) {
            throw new StorageException("Failed to read upload for encrypted storage.", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String storageKey, long fileSize) {
        if (inputStream == null) throw new StorageException("Cannot store a null input stream.");
        Path destination = resolveSafePath(storageKey);
        try {
            Path parent = destination.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (InputStream source = inputStream;
                 var fileOutput = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                 var encryptedOutput = encryptionService.encryptingStream(fileOutput)) {
                source.transferTo(encryptedOutput);
            }
            log.debug("Stored encrypted object at {}", destination);
            return storageKey;
        } catch (IOException e) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw new StorageException("Failed to encrypt and store file: " + storageKey, e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path file = resolveSafePath(storageKey);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("Encrypted file not found or unreadable: " + storageKey);
        }
        try {
            return new InputStreamResource(
                    encryptionService.decryptingStream(Files.newInputStream(file)));
        } catch (IOException e) {
            throw new StorageException("Could not open encrypted file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafePath(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete encrypted file {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolveSafePath(storageKey));
    }

    private Path resolveSafePath(String storageKey) {
        Path resolved = rootLocation.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootLocation)) {
            throw new StorageException("Cannot access storage outside the configured root directory.");
        }
        return resolved;
    }
}
