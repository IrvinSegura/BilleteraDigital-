package com.irvinsegura.wallet.support.domain.model;

import java.util.List;

/**
 * A way the bot failed someone, to find what its knowledge files are missing.
 */
public enum BotMissKind {
    // It answered and the user said it didn't help
    NOT_HELPED,
    // It could read the question but knew nothing about it
    NOT_FOUND,
    // It understood only part and offered what it may have meant
    SUGGESTED,
    // It couldn't read it: mostly noise, so only when asked for
    ASKED_AGAIN;

    // What a report shows unless it asks for one kind
    public static final List<BotMissKind> USEFUL = List.of(NOT_HELPED, NOT_FOUND, SUGGESTED);
}
