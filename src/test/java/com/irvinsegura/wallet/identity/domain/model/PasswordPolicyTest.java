package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThatCode(() -> PasswordPolicy.check("correct horse battery staple")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.check("short-pass1"))
                .isInstanceOf(WeakPasswordException.class)
                .extracting("code").isEqualTo("WEAK_PASSWORD");
    }

    @Test
    void rejectsBlankPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.check("            ")).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void rejectsPasswordsThatBcryptWouldTruncate() {
        // 30 characters but 90 bytes in UTF-8
        assertThatThrownBy(() -> PasswordPolicy.check("€".repeat(30))).isInstanceOf(WeakPasswordException.class);
    }
}
