package com.irvinsegura.wallet.identity.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email address normalized to lower case, so uniqueness does not depend on how the user typed it.
 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    // Deliberately simple: a regex cannot prove an address exists, only reject obvious typos
    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Email {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException();
        }
    }

    public static Email of(String raw) {
        return new Email(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
