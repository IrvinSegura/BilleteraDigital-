package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class IdempotencyKeyReusedException extends DomainException {

    public IdempotencyKeyReusedException() {
        super("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different request");
    }
}
