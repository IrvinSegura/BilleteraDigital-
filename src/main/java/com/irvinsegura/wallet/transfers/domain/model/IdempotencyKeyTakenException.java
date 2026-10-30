package com.irvinsegura.wallet.transfers.domain.model;

/**
 * Another request with the same key committed first. Not a client error: the caller replays that request's result.
 */
public class IdempotencyKeyTakenException extends RuntimeException {

    public IdempotencyKeyTakenException() {
        super("Idempotency key already claimed by a committed request");
    }
}
