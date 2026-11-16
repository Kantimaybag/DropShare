package com.sharedrop.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator(
                "jpg,png,pdf,txt,zip",
                100 * 1024 * 1024, // 100 MB
                20,
                1024 * 1024        // 1 MB
        );
    }

    @Test
    @DisplayName("Should allow valid file extensions regardless of case")
    void testAllowedExtensions() {
        assertTrue(validator.isAllowedExtension("photo.JPG"));
        assertTrue(validator.isAllowedExtension("document.pdf"));
        assertTrue(validator.isAllowedExtension("archive.zip"));
        assertTrue(validator.isAllowedExtension("readme.txt"));
    }

    @Test
    @DisplayName("Should reject disallowed file extensions")
    void testDisallowedExtensions() {
        assertFalse(validator.isAllowedExtension("malware.exe"));
        assertFalse(validator.isAllowedExtension("script.bat"));
        assertFalse(validator.isAllowedExtension("trojan.vbs"));
    }

    @Test
    @DisplayName("Should sanitize filenames preventing path traversal")
    void testSanitizeFilename() {
        assertEquals("report.pdf", validator.sanitizeFilename("../../report.pdf"));
        assertEquals("notes.txt", validator.sanitizeFilename("..\\..\\notes.txt"));
        assertEquals("data.csv", validator.sanitizeFilename("/etc/passwd/data.csv"));
        assertEquals("unnamed", validator.sanitizeFilename(""));
        assertEquals("unnamed", validator.sanitizeFilename(null));
        assertEquals("unnamed", validator.sanitizeFilename("..//.."));
    }

    @Test
    @DisplayName("Should enforce size constraints")
    void testSizeConstraints() {
        assertTrue(validator.isAllowedSize(1024));
        assertTrue(validator.isAllowedSize(100 * 1024 * 1024));
        assertFalse(validator.isAllowedSize(0));
        assertFalse(validator.isAllowedSize(-1));
        assertFalse(validator.isAllowedSize(100 * 1024 * 1024 + 1));
    }

    @Test
    @DisplayName("Should enforce file count constraints")
    void testFileCountConstraints() {
        assertTrue(validator.isAllowedFileCount(1));
        assertTrue(validator.isAllowedFileCount(20));
        assertFalse(validator.isAllowedFileCount(0));
        assertFalse(validator.isAllowedFileCount(21));
    }

    @Test
    @DisplayName("Should correctly extract file extension")
    void testGetExtension() {
        assertEquals("pdf", validator.getExtension("document.pdf"));
        assertEquals("gz", validator.getExtension("archive.tar.gz"));
        assertEquals("", validator.getExtension("Makefile"));
        assertEquals("", validator.getExtension(null));
    }
}
