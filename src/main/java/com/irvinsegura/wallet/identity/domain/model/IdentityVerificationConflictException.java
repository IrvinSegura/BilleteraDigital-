package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The verification can't go on as asked. {@link #reason()} is one of the constants here.
 */
public class IdentityVerificationConflictException extends DomainException {

    /** One is already waiting for review. */
    public static final String PENDING = "PENDING";
    /** The identity is already verified. */
    public static final String VERIFIED = "VERIFIED";
    /** Another account already uses this card. */
    public static final String DOCUMENT_IN_USE = "DOCUMENT_IN_USE";
    /** Someone reviewed it first. */
    public static final String ALREADY_REVIEWED = "ALREADY_REVIEWED";
    /** A verified or pending identity fixes the account's name. */
    public static final String NAME_LOCKED = "NAME_LOCKED";

    private final String reason;

    public IdentityVerificationConflictException(String reason) {
        super("IDENTITY_VERIFICATION_CONFLICT", "Identity verification conflict: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
