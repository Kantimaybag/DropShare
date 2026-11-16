package com.sharedrop.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Storage service abstraction allowing seamless swapping between
 * local filesystem and S3-compatible cloud object storage.
 */
public interface StorageService {

    /**
     * Initializes the storage backend (e.g. creates local upload directory or verifies bucket).
     */
    void init();

    /**
     * Stores an uploaded multipart file using streaming I/O.
     *
     * @param file       the multipart file from HTTP request
     * @param storageKey unique relative path or S3 key
     * @return the resolved storage key
     */
    String store(MultipartFile file, String storageKey);

    /**
     * Stores content from an input stream without buffering the entire payload into heap.
     *
     * @param inputStream the streaming input source
     * @param storageKey  unique relative path or S3 key
     * @param fileSize    expected file size in bytes
     * @return the resolved storage key
     */
    String store(InputStream inputStream, String storageKey, long fileSize);

    /**
     * Loads a stored file as a Spring Resource for streaming downloads.
     *
     * @param storageKey the key under which file was stored
     * @return the readable Resource
     */
    Resource loadAsResource(String storageKey);

    /**
     * Deletes a stored file if it exists.
     *
     * @param storageKey the key to delete
     */
    void delete(String storageKey);

    /**
     * Checks if a storage key exists.
     *
     * @param storageKey the key to check
     * @return true if the object/file exists
     */
    boolean exists(String storageKey);
}
