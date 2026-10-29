package com.irvinsegura.wallet.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

public record RefreshTokenPolicy(Duration timeToLive) {

    public RefreshTokenPolicy {
        Objects.requireNonNull(timeToLive, "timeToLive must not be null");
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }
    }
}
