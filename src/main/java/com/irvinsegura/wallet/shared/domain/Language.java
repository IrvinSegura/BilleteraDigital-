package com.irvinsegura.wallet.shared.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A language the app and its emails are available in. Spanish is the default, as in the client apps, until the user
 * picks another one.
 */
public enum Language {
    ES("es"),
    EN("en");

    public static final Language DEFAULT = ES;

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    /**
     * @return the IETF language tag, as stored and sent over the API
     */
    public String tag() {
        return tag;
    }

    public static Optional<Language> ofTag(String tag) {
        return Arrays.stream(values()).filter(language -> language.tag.equalsIgnoreCase(tag)).findFirst();
    }
}
