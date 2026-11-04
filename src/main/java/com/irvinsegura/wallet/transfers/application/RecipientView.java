package com.irvinsegura.wallet.transfers.application;

import java.time.Instant;

/**
 * Someone the user can send money to.
 *
 * @param displayName first name and the initials of the rest, enough to recognize the person without exposing their
 *                    full name to anyone who types an email
 * @param lastSentAt  when the user last sent them money; {@code null} if never
 * @param timesSent   how many transfers the user has sent them
 */
public record RecipientView(String email, String displayName, Instant lastSentAt, long timesSent) {

    /**
     * "Irvin Segura López" becomes "Irvin S. L."; a single name stays as it is.
     */
    static String displayName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.strip().split("\\s+");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            name.append(' ').append(parts[i].codePoints().limit(1)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)).append('.');
        }
        return name.toString();
    }
}
