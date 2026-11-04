package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM with a random 96-bit nonce per value, stored as Base64 of nonce followed by ciphertext and tag. GCM
 * also authenticates, so a value tampered with in the database fails to decrypt instead of yielding another secret.
 */
class AesGcmSecretCipher implements SecretCipher {

    static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    AesGcmSecretCipher(byte[] key) {
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("The key must be %d bytes".formatted(KEY_BYTES));
        }
        this.key = new SecretKeySpec(key, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is required on every Java platform", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] stored = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            return new String(cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a stored secret; was the key changed?", e);
        }
    }
}
