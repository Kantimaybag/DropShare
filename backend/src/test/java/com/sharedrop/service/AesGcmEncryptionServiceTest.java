package com.sharedrop.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionServiceTest {
    private final AesGcmEncryptionService encryption =
            new AesGcmEncryptionService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void encryptsAndDecryptsText() {
        String original = "Sensitive ShareDrop text";
        String encrypted = encryption.encryptText(original);

        assertNotEquals(original, encrypted);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertEquals(original, encryption.decryptText(encrypted));
    }

    @Test
    void rejectsTamperedTextCiphertext() {
        String encrypted = encryption.encryptText("secret");
        int tamperIndex = "enc:v1:".length() + 16; // first ciphertext byte after the 12-byte nonce
        char replacement = encrypted.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, tamperIndex) + replacement + encrypted.substring(tamperIndex + 1);

        assertThrows(IllegalStateException.class, () -> encryption.decryptText(tampered));
    }

    @Test
    void encryptsAndDecryptsFileStream() throws Exception {
        byte[] original = "A file payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        try (var encrypted = encryption.encryptingStream(stored)) {
            encrypted.write(original);
        }

        byte[] ciphertext = stored.toByteArray();
        assertFalse(java.util.Arrays.equals(original, ciphertext));

        byte[] restored;
        try (var decrypted = encryption.decryptingStream(new ByteArrayInputStream(ciphertext))) {
            restored = decrypted.readAllBytes();
        }
        assertArrayEquals(original, restored);
    }

    @Test
    void readsLegacyPlaintextFilesForBackwardCompatibility() throws Exception {
        byte[] legacy = "legacy upload".getBytes(StandardCharsets.UTF_8);
        try (var input = encryption.decryptingStream(new ByteArrayInputStream(legacy))) {
            assertArrayEquals(legacy, input.readAllBytes());
        }
    }
}
