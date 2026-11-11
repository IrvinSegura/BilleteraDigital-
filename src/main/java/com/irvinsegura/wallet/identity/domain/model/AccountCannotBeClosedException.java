package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The account cannot be closed yet, or ever. {@link #reason()} says why, so the app can tell the owner what to do.
 */
public class AccountCannotBeClosedException extends DomainException {

    public static final String ADMIN = "ADMIN";
    public static final String NOT_ACTIVE = "NOT_ACTIVE";

    private final String reason;

    /**
     * @param reason {@link #ADMIN}, {@link #NOT_ACTIVE}, or what an {@code AccountClosureCheck} found
     */
    public AccountCannotBeClosedException(String reason) {
        super("ACCOUNT_CANNOT_BE_CLOSED", "The account cannot be closed: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
