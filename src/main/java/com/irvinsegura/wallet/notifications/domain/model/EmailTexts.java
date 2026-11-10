package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The wording of the emails in one language, from {@code emails_<language>.properties} next to this class. Texts name
 * their values, as in {@code Hola {name},}.
 */
final class EmailTexts {

    private static final Locale MEXICO = Locale.of("es", "MX");
    private static final String BUNDLE = EmailTexts.class.getPackageName() + ".emails";

    private final Language language;
    private final ResourceBundle bundle;
    private final DateTimeFormatter dateTime;

    EmailTexts(Language language, ZoneId zone) {
        this.language = language;
        Locale locale = language == Language.ES ? MEXICO : Locale.US;
        // No fallback, so a text missing from one language fails instead of going out in another one
        this.bundle = ResourceBundle.getBundle(BUNDLE, Locale.of(language.tag()),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        this.dateTime = DateTimeFormatter
                .ofPattern(language == Language.ES ? "d 'de' MMMM 'de' yyyy, HH:mm z" : "MMMM d, yyyy, h:mm a z", locale)
                .withZone(zone);
    }

    Language language() {
        return language;
    }

    String get(String key) {
        return get(key, Map.of());
    }

    String get(String key, Map<String, String> values) {
        String text = bundle.getString(key);
        for (Map.Entry<String, String> value : values.entrySet()) {
            text = text.replace("{" + value.getKey() + "}", value.getValue());
        }
        return text;
    }

    /**
     * @return the text, or null if this email doesn't have it
     */
    String optional(String key, Map<String, String> values) {
        return bundle.containsKey(key) ? get(key, values) : null;
    }

    /**
     * The paragraphs of an email: {@code key}, then {@code key.2} and so on while they exist.
     */
    List<String> paragraphs(String key, Map<String, String> values) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(get(key, values));
        for (int i = 2; bundle.containsKey(key + "." + i); i++) {
            paragraphs.add(get(key + "." + i, values));
        }
        return paragraphs;
    }

    String dateTime(Instant instant) {
        return dateTime.format(instant);
    }

    /**
     * Money reads the same in both languages, as in the apps: {@code $1,250.50 MXN}. The code stays visible because
     * USD and MXN share the "$" symbol.
     *
     * @param amount a plain decimal number, such as {@code 1250.50}
     */
    static String money(String amount, String currency) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(new BigDecimal(amount)) + " " + currency;
    }
}
