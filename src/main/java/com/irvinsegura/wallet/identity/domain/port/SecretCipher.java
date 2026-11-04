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
}
