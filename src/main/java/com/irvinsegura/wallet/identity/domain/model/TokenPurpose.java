package com.irvinsegura.wallet.identity.domain.model;

/**
 * What a one-time token proves. A token only works for its own purpose, so a verification link can never reset a
 * password.
 */
public enum TokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
