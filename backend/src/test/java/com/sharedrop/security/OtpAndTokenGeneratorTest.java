package com.sharedrop.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OtpAndTokenGeneratorTest {

    @Test
    @DisplayName("Generated OTP should be exactly 6 numeric digits")
    void testOtpGenerationFormat() {
        for (int i = 0; i < 50; i++) {
            String otp = OtpGenerator.generate();
            assertNotNull(otp);
            assertEquals(6, otp.length());
            assertTrue(otp.matches("^\\d{6}$"), "OTP should be 6 digits: " + otp);
        }
    }

    @Test
    @DisplayName("Generated tokens should be high entropy, URL safe, and unique")
    void testTokenUniqueness() {
        Set<String> tokens = new HashSet<>();
        int count = 100;
        for (int i = 0; i < count; i++) {
            String token = TokenGenerator.generateToken();
            assertNotNull(token);
            assertTrue(token.length() >= 24, "Token should be at least 24 characters: " + token);
            // URL safe Base64 characters: a-z, A-Z, 0-9, -, _
            assertTrue(token.matches("^[A-Za-z0-9_-]+$"), "Token must be URL-safe: " + token);
            tokens.add(token);
        }
        assertEquals(count, tokens.size(), "All generated tokens should be unique");
    }
}
