package com.irvinsegura.wallet.support.domain.model;

/**
 * What the bot did with a message, see {@link BotReply#outcome()}.
 */
public enum BotOutcome {
    // An answer the user can say helped or not
    ANSWERED,
    // A greeting or thanks, which is no question
    SMALL_TALK,
    // It went to a person: a problem, or an answer that hands it over
    PASSED_ON,
    // It understood only part of it and offered what it may have meant
    SUGGESTED,
    // It couldn't read it and asked again
    ASKED_AGAIN,
    // It could read it but knew nothing about it
    NOT_FOUND
}
