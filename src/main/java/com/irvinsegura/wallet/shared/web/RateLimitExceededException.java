package com.irvinsegura.wallet.shared.web;

import java.time.Duration;

/**
 * Raised by {@link RateLimitFilter} and turned into a {@code 429} with {@code Retry-After} by the global handler.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests");
        this.retryAfter = retryAfter;
    }

    /**
     * Whole seconds, rounded up so a client that waits exactly this long is let through.
     */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds();
        return retryAfter.equals(Duration.ofSeconds(seconds)) ? Math.max(seconds, 1) : seconds + 1;
    }
}
