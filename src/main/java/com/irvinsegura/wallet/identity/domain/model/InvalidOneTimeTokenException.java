package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Same error for an unknown, expired, already used or replaced link, so responses reveal nothing about tokens.
 */
public class InvalidOneTimeTokenException extends DomainException {

    public InvalidOneTimeTokenException() {
        super("INVALID_TOKEN", "The link is invalid or has expired; request a new one");
    }
}
