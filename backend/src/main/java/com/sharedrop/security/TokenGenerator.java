package com.sharedrop.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically secure share tokens.
 * Each token has at least 128 bits of randomness, encoded as URL-safe Base64.
 */
public class TokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 24; // 192 bits → 32-char base64url

    private TokenGenerator() {}

    /**
     * Generates a URL-safe, base64-encoded random token.
     * @return token string (32 characters, no padding)
     */
    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
