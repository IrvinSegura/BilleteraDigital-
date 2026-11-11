package com.irvinsegura.wallet.identity.domain.port;

/**
 * Encrypts secrets the application must read back, such as authenticator app secrets, with a key kept out of the
 * database.
 */
public interface SecretCipher {

    String encrypt(String plaintext);

    /**
     * @throws IllegalStateException if the value was not encrypted with the current key
     */
    String decrypt(String ciphertext);

    /**
     * Encrypts a file, such as a picture of an identity document.
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * @throws IllegalStateException if the value was not encrypted with the current key
     */
    byte[] decrypt(byte[] ciphertext);

    /**
     * A keyed hash of {@code value}, the same every time, to find equal values (a card already in use) without
     * storing them in clear. Without the key it can't be checked against a list of guesses.
     */
    String fingerprint(String value);
}
