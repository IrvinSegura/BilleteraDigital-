package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.shared.domain.Language;

/**
 * @param language the language of the app signing up, which the account's emails will use; null for the default
 */
public record RegisterUserCommand(String email, String password, String fullName, Language language) {

    // Keeps the password out of logs and exception messages that print the command
    @Override
    public String toString() {
        return "RegisterUserCommand[email=%s, fullName=%s]".formatted(email, fullName);
    }
}
