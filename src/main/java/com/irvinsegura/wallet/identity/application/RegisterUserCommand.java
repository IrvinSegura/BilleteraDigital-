package com.irvinsegura.wallet.identity.application;

public record RegisterUserCommand(String email, String password, String fullName) {

    // Keeps the password out of logs and exception messages that print the command
    @Override
    public String toString() {
        return "RegisterUserCommand[email=%s, fullName=%s]".formatted(email, fullName);
    }
}
