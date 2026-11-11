package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The account still has the password it was emailed, which also sits in an inbox, so it can do nothing until its owner
 * replaces it.
 */
public class PasswordChangeRequiredException extends DomainException {

    public PasswordChangeRequiredException() {
        super("PASSWORD_CHANGE_REQUIRED", "Change the password this account was given before using it");
    }
}
