package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * What was sent can't be a valid voter card of an adult. {@link #reason()} says which part, so the app can point at
 * it.
 */
public class InvalidIdentityDocumentException extends DomainException {

    public static final String NAME_INVALID = "NAME_INVALID";
    public static final String CURP_INVALID = "CURP_INVALID";
    public static final String VOTER_KEY_INVALID = "VOTER_KEY_INVALID";
    public static final String CIC_INVALID = "CIC_INVALID";
    public static final String CITIZEN_ID_INVALID = "CITIZEN_ID_INVALID";
    /** The CURP and the voter key don't carry the same birth date and sex. */
    public static final String DATA_MISMATCH = "DATA_MISMATCH";
    public static final String EXPIRED = "EXPIRED";
    public static final String UNDERAGE = "UNDERAGE";
    /** A picture that isn't a JPEG or PNG photo, or is too small or too big to read. */
    public static final String IMAGE_INVALID = "IMAGE_INVALID";

    private final String reason;

    public InvalidIdentityDocumentException(String reason) {
        super("INVALID_IDENTITY_DOCUMENT", "The identity document is not valid: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
