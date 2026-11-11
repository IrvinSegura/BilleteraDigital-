package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
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

    private static final String FINGERPRINT_LABEL = "wallet-fingerprint-v1";

    private final SecretKey key;
    // Derived from the key, so the same secret never serves two purposes
    private final SecretKey fingerprintKey;

    AesGcmSecretCipher(byte[] key) {
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("The key must be %d bytes".formatted(KEY_BYTES));
        }
        this.key = new SecretKeySpec(key, "AES");
        this.fingerprintKey = new SecretKeySpec(hmac(new SecretKeySpec(key, "HmacSHA256"),
                FINGERPRINT_LABEL.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    @Override
    public String encrypt(String plaintext) {
        return Base64.getEncoder().encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decrypt(String ciphertext) {
        byte[] stored;
        try {
            stored = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a stored secret; was the key changed?", e);
        }
        return new String(decrypt(stored), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is required on every Java platform", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] stored) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            return cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a stored secret; was the key changed?", e);
        }
    }

    @Override
    public String fingerprint(String value) {
        return HexFormat.of().formatHex(hmac(fingerprintKey, value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(SecretKey key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required on every Java platform", e);
        }
    }
}
