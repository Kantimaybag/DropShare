package com.sharedrop.security;

import com.sharedrop.exception.RateLimitException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory sliding-window rate limiter.
 * For distributed deployments, replace with Redis-based implementation.
 */
@Service
public class RateLimitService {

    private final Map<String, Queue<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Checks if the request is allowed under the given rate limit.
     * @param key       unique identifier (e.g., IP + endpoint)
     * @param maxRequests maximum requests allowed in the window
     * @param window    sliding window duration
     * @throws RateLimitException if rate limit exceeded
     */
    public void checkRateLimit(String key, int maxRequests, Duration window) {
        Queue<Instant> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        // Purge entries outside the window
        while (!timestamps.isEmpty() && timestamps.peek().isBefore(cutoff)) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxRequests) {
            throw new RateLimitException("Rate limit exceeded. Try again later.");
        }

        timestamps.add(now);
    }

    /** Convenience: check rate limit using requests-per-minute. */
    public void checkPerMinute(String key, int maxPerMinute) {
        checkRateLimit(key, maxPerMinute, Duration.ofMinutes(1));
    }

    /** Periodic cleanup of stale entries. Called by CleanupScheduler. */
    public void purgeStaleEntries() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        requestLog.entrySet().removeIf(entry -> {
            Queue<Instant> q = entry.getValue();
            q.removeIf(ts -> ts.isBefore(cutoff));
            return q.isEmpty();
        });
    }
}
