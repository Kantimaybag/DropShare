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
import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
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
    private static final byte[] MAGIC = new byte[] { 'S', 'D', 'G', '1' };
    private static final String TEXT_PREFIX = "enc:v1:";
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
            destination.write(MAGIC);
            destination.write(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new CipherOutputStream(destination, cipher);
        } catch (GeneralSecurityException ex) {
            try { destination.close(); } catch (IOException ignored) { }
            throw new IOException("Could not initialize AES-GCM encryption.", ex);
        }
    }

    /** Encrypts small text values for database storage using a fresh nonce per value. */
    public String encryptText(String plaintext) {
        if (plaintext == null) return null;
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return TEXT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt shared text.", ex);
        }
    }

    /** Decrypts text values. Unprefixed values are legacy plaintext rows from before encryption. */
    public String decryptText(String storedValue) {
        if (storedValue == null || !storedValue.startsWith(TEXT_PREFIX)) return storedValue;
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(TEXT_PREFIX.length()));
            if (payload.length <= NONCE_LENGTH) throw new IllegalArgumentException("Invalid encrypted text payload.");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt shared text. Check the encryption key and stored data integrity.", ex);
        }
    }

    public InputStream decryptingStream(InputStream encrypted) throws IOException {
        byte[] header = encrypted.readNBytes(MAGIC.length);
        if (header.length != MAGIC.length) {
            encrypted.close();
            throw new IOException("Stored object is truncated.");
        }
        boolean encryptedObject = true;
        for (int i = 0; i < MAGIC.length; i++) encryptedObject &= header[i] == MAGIC[i];
        if (!encryptedObject) {
            // Backward compatibility for files uploaded before at-rest encryption was enabled.
            return new SequenceInputStream(new ByteArrayInputStream(header), encrypted);
        }
        byte[] nonce = encrypted.readNBytes(NONCE_LENGTH);
        if (nonce.length != NONCE_LENGTH) {
            encrypted.close();
            throw new IOException("Encrypted object is truncated or has an invalid nonce.");
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
