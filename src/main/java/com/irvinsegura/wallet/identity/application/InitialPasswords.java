package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.CommonPasswordException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.WeakPasswordException;
import java.security.SecureRandom;

/**
 * Generates the password a new staff account starts with: about 92 random bits, in groups that are easy to copy,
 * without the characters that are easy to mix up.
 */
final class InitialPasswords {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GROUPS = 4;
    private static final int GROUP_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InitialPasswords() {
    }

    /**
     * @param personal what it must not be built from, as for any other password: the email and name of its owner
     */
    static String generate(String... personal) {
        while (true) {
            String password = random();
            if (acceptable(password, personal)) {
                return password;
            }
        }
    }

    private static String random() {
        StringBuilder password = new StringBuilder();
        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                password.append('-');
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return password.toString();
    }

    private static boolean acceptable(String password, String... personal) {
        try {
            PasswordPolicy.check(password, personal);
            return true;
        } catch (WeakPasswordException | CommonPasswordException e) {
            return false;
        }
    }
}
