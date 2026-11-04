package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The password appears in a known data breach, so attackers already try it against every site.
 */
public class BreachedPasswordException extends DomainException {

    public BreachedPasswordException() {
        super("BREACHED_PASSWORD", "Password appears in a known data breach");
    }
}
