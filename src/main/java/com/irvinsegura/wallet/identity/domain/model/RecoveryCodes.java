package com.irvinsegura.wallet.identity.domain.model;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-use codes that stand in for the authenticator app when the phone is lost. Shown once; only their hash is
 * stored, which is enough because each holds 50 random bits and its use is counted toward the lockout.
 */
public final class RecoveryCodes {

    public static final int COUNT = 10;
    private static final int GROUP_LENGTH = 5;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    /**
     * New codes such as {@code k3x7q-m2pzt}.
     */
    public static List<String> generate() {
        List<String> codes = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            codes.add(group() + "-" + group());
        }
        return codes;
    }

    /**
     * Hash of a code as the user may type it: any case, with or without the dash and spaces.
     */
    public static String hash(String code) {
        return OpaqueTokens.hash(code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT));
    }

    /**
     * Whether it has the shape of a recovery code rather than an authenticator code.
     */
    public static boolean looksLikeRecoveryCode(String code) {
        return code.replaceAll("[\\s-]", "").length() == 2 * GROUP_LENGTH;
    }

    private static String group() {
        StringBuilder group = new StringBuilder(GROUP_LENGTH);
        for (int i = 0; i < GROUP_LENGTH; i++) {
            group.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return group.toString();
    }
}
