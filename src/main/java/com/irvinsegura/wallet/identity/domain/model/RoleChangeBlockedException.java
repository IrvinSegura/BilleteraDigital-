package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * A customer can't become staff while something of theirs is still in the app, such as money in a wallet: staff can't
 * move money, so it would be stuck. {@link #reason()} is what an {@code AccountClosureCheck} found.
 */
public class RoleChangeBlockedException extends DomainException {

    private final String reason;

    public RoleChangeBlockedException(String reason) {
        super("ROLE_CHANGE_BLOCKED", "The account can't become staff yet: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
