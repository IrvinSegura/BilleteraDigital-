package com.irvinsegura.wallet.support;

import java.time.Duration;
import java.time.Instant;

/**
 * Retries an assertion until it passes or times out, for effects produced by asynchronous event listeners.
 */
public final class Eventually {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INTERVAL = Duration.ofMillis(100);

    private Eventually() {
    }

    public static void assertEventually(ThrowingRunnable assertion) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                if (Instant.now().isAfter(deadline)) {
                    throw e;
                }
                Thread.sleep(INTERVAL);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
