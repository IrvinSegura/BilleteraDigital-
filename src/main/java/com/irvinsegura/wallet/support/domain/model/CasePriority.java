package com.irvinsegura.wallet.support.domain.model;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How soon a case in the queue should reach an agent: someone whose money didn't arrive, was charged or is missing
 * goes before a question about the app, and those of the same priority by how long they have waited.
 */
public enum CasePriority {
    NORMAL,
    HIGH;

    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");
    // The beginnings of the words people use when writing about their money, in both languages, without accents
    private static final List<String> MONEY_WORDS = List.of("dinero", "retir", "deposit", "transfer", "cobr", "cargo",
            "saldo", "pago", "pague", "spei", "clabe", "reembols", "devoluc", "devolv", "fraud", "robo", "robaron",
            "money", "withdraw", "charg", "balance", "payment", "refund", "stolen");

    /**
     * The priority of a case from what the customer wrote before it reached the queue.
     */
    public static CasePriority of(Collection<String> texts) {
        return texts.stream().filter(Objects::nonNull).flatMap(text -> words(text).stream())
                .anyMatch(word -> MONEY_WORDS.stream().anyMatch(word::startsWith)) ? HIGH : NORMAL;
    }

    private static List<String> words(String text) {
        String folded = ACCENTS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        return NON_LETTERS.splitAsStream(folded).filter(word -> !word.isEmpty()).toList();
    }
}
