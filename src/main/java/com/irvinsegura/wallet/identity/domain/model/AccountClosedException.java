package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Sign-in to an account its owner closed.
 */
public class AccountClosedException extends DomainException {

    public AccountClosedException() {
        super("ACCOUNT_CLOSED", "The account was closed by its owner");
    }
}
