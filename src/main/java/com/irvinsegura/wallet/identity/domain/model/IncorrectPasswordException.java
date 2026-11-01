package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The current password given to change it is wrong. Not a 401: the access token is fine, and clients that treat 401
 * as "signed out" would log the user out.
 */
public class IncorrectPasswordException extends DomainException {

    public IncorrectPasswordException() {
        super("INCORRECT_PASSWORD", "Current password is incorrect");
    }
}
