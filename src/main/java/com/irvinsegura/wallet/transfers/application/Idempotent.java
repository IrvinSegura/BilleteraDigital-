package com.irvinsegura.wallet.transfers.application;

/**
 * Result of an idempotent operation. {@code replayed} is true when an earlier request with the same key had already
 * produced it and nothing was executed this time.
 */
public record Idempotent<T>(T value, boolean replayed) {
}
