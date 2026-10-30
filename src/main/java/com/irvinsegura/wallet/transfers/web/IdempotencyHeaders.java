package com.irvinsegura.wallet.transfers.web;

final class IdempotencyHeaders {

    static final String KEY = "Idempotency-Key";
    // Tells the client the response comes from an earlier request and nothing moved this time
    static final String REPLAYED = "Idempotent-Replayed";
    static final String KEY_PATTERN = "[A-Za-z0-9_-]{1,100}";
    static final String KEY_MESSAGE = "must be 1 to 100 letters, digits, - or _ (a UUID works)";

    private IdempotencyHeaders() {
    }
}
