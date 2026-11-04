package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"correct horse battery staple", "Tacos al pastor con piña 7", "e2e-pass-3f9a1c0b77d2",
            "mi perro Firulais come croquetas", "Lp9#vT2!qZx8", "admin-dev-password"})
    void acceptsPasswordsThatAreHardToGuess(String password) {
        assertThatCode(() -> PasswordPolicy.check(password, "ana.lopez@example.com", "Ana López"))
                .doesNotThrowAnyException();
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

    @ParameterizedTest
    @ValueSource(strings = {
            // Few different characters, or a piece repeated
            "aaaaaaaaaaaa", "121212121212", "abcabcabcabc", "passwordpassword", "Teamo-Teamo-Teamo",
            // Sequences and keyboard walks, either way and through spaces or symbols
            "123456789012", "1234 5678 9012", "abcdefghijkl", "210987654321", "qwertyuiopas", "1q2w3e4r5t6y",
            "1qaz2wsx3edc", "asdfghjklzxc",
            // A common word dressed up with digits, symbols, accents or look-alike characters
            "Password2024!", "P@ssw0rd2024!", "Contraseña123", "iloveyou1234", "Billetera2026$", "@dmin12345678",
            "MEXICO2026!!", "Te4mo!!20242024"})
    void rejectsPasswordsThatAreEasyToGuess(String password) {
        assertThatThrownBy(() -> PasswordPolicy.check(password))
                .isInstanceOf(CommonPasswordException.class)
                .extracting("code").isEqualTo("COMMON_PASSWORD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AnaLopez1990!", "lopez-2024-2024", "ana.lopez.123", "Lopez$$123456"})
    void rejectsPasswordsBuiltFromTheUsersOwnNameOrEmail(String password) {
        assertThatThrownBy(() -> PasswordPolicy.check(password, "ana.lopez@example.com", "Ana López"))
                .isInstanceOf(CommonPasswordException.class);
    }

    @Test
    void shortNamesAreNotTreatedAsPersonal() {
        // "Ana" alone is too short to rule out: it shows up inside many good passphrases
        assertThatCode(() -> PasswordPolicy.check("ana12345678#q", "ana@example.com", "Ana"))
                .doesNotThrowAnyException();
    }
}
