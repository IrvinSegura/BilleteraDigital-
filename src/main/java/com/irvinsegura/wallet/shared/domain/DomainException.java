package com.irvinsegura.wallet.shared.domain;

/**
 * Base type for business rule violations. The code is a stable identifier that API clients can rely on.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
