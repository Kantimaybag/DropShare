package com.sharedrop.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographic hashing utilities.
 * SHA-256 for token hashing (deterministic lookup).
 * BCrypt for OTP hashing (brute-force resistant).
 */
public final class HashUtil {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private HashUtil() {}

    /** SHA-256 hash, returned as lowercase hex. Used for share token lookup. */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** BCrypt hash. Used for OTP storage. */
    public static String bcryptHash(String rawValue) {
        return BCRYPT.encode(rawValue);
    }

    /** BCrypt verification. */
    public static boolean bcryptVerify(String rawValue, String hash) {
        return BCRYPT.matches(rawValue, hash);
    }
}
