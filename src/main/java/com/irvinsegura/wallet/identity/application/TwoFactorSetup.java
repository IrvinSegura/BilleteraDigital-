package com.irvinsegura.wallet.identity.application;

/**
 * What the user needs to add the account to an authenticator app: the secret to type in, or the URI to show as a
 * QR code. Shown once.
 *
 * @param otpauthUri {@code otpauth://totp/...}, the format authenticator apps scan
 */
public record TwoFactorSetup(String secret, String otpauthUri) {

    @Override
    public String toString() {
        return "TwoFactorSetup[***]";
    }
}
