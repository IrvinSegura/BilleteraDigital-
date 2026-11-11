package com.irvinsegura.wallet.identity.application;

/**
 * What a customer sends to verify their identity: the voter card's data as printed on it, and three pictures (JPEG
 * or PNG): the card's front and back, and their face.
 */
public record IdentityVerificationCommand(String firstNames, String firstSurname, String secondSurname, String curp,
        String voterKey, String cic, String citizenId, int expiryYear, byte[] front, byte[] back, byte[] selfie) {
}
