package com.sharedrop.service;

import com.sharedrop.exception.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Server-side AES-256-GCM encryption for uploaded content.
 * Each stored object starts with a fresh 12-byte nonce followed by ciphertext and
 * the GCM authentication tag. The encryption key must be supplied through the
 * SHARE_ENCRYPTION_KEY environment variable (Base64-encoded 32-byte key).
 */
@Service
public class AesGcmEncryptionService {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public AesGcmEncryptionService(@Value("${app.security.encryption-key}") String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("SHARE_ENCRYPTION_KEY must decode to exactly 32 bytes.");
            }
            this.key = new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid AES-256 key configuration. Set SHARE_ENCRYPTION_KEY to a Base64-encoded 32-byte key.", ex);
        }
    }

    public OutputStream encryptingStream(OutputStream destination) throws IOException {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            destination.write(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new CipherOutputStream(destination, cipher);
        } catch (GeneralSecurityException ex) {
            try { destination.close(); } catch (IOException ignored) { }
            throw new IOException("Could not initialize AES-GCM encryption.", ex);
        }
    }

    public InputStream decryptingStream(InputStream encrypted) throws IOException {
        byte[] nonce = encrypted.readNBytes(NONCE_LENGTH);
        if (nonce.length != NONCE_LENGTH) {
            encrypted.close();
            throw new IOException("Encrypted object is truncated or has an invalid header.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new CipherInputStream(encrypted, cipher);
        } catch (GeneralSecurityException ex) {
            encrypted.close();
            throw new IOException("Could not initialize AES-GCM decryption.", ex);
        }
    }
}
