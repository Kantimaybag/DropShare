package com.sharedrop.security;

import com.sharedrop.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    @DisplayName("Should allow requests within configured threshold")
    void testRateLimitWithinThreshold() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() ->
                    rateLimitService.checkRateLimit("client-1", 5, Duration.ofSeconds(10))
            );
        }
    }

    @Test
    @DisplayName("Should throw RateLimitException when threshold exceeded")
    void testRateLimitExceeded() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.checkRateLimit("client-2", 3, Duration.ofSeconds(10));
        }

        assertThrows(RateLimitException.class, () ->
                rateLimitService.checkRateLimit("client-2", 3, Duration.ofSeconds(10))
        );
    }
}
