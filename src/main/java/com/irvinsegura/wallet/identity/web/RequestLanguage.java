package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.shared.domain.Language;
import java.util.Locale;
import java.util.Optional;

/**
 * The language a request asks for in its {@code Accept-Language} header, for the emails sent before there is a session
 * to read the user's language from. Client apps send the language they are showing.
 */
final class RequestLanguage {

    private RequestLanguage() {
    }

    /**
     * @return the first language in the header's order of preference that emails exist in; null if none is, or the
     *         header is missing or malformed
     */
    static Language of(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        try {
            return Locale.LanguageRange.parse(acceptLanguage).stream()
                    .map(range -> Language.ofTag(range.getRange().split("-", 2)[0]))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
