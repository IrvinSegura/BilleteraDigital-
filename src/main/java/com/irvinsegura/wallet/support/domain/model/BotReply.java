package com.irvinsegura.wallet.support.domain.model;

import java.util.List;

/**
 * What the bot does with what someone wrote.
 *
 * @param text         what it says; {@code null} when it just passes the conversation on
 * @param asksFeedback an answer the user can say helped or not
 * @param escalate     the conversation also goes to a person
 * @param suggestions  questions it thinks were meant, which the user can pick instead of writing again
 * @param unanswered   it can read it but knows nothing about it: said twice in a row, it goes to a person
 * @param topic        the title of the section it answered with; {@code null} when it found none
 */
public record BotReply(String text, boolean asksFeedback, boolean escalate, List<String> suggestions,
        boolean unanswered, String topic) {

    public BotReply {
        suggestions = List.copyOf(suggestions);
    }

    public static BotReply answering(KnowledgeSection section) {
        return new BotReply(section.body(), !section.escalate() && !section.smallTalk(), section.escalate(), List.of(),
                false, section.title());
    }

    public static BotReply askingAgain(String text) {
        return new BotReply(text, false, false, List.of(), false, null);
    }

    public static BotReply suggesting(String text, List<String> questions) {
        return new BotReply(text, false, false, questions, false, null);
    }

    public static BotReply notKnowing(String text) {
        return new BotReply(text, false, false, List.of(), true, null);
    }

    public static BotReply passingToAPerson() {
        return new BotReply(null, false, true, List.of(), false, null);
    }

    /**
     * What it did with the message, for the report of where it fails.
     */
    public BotOutcome outcome() {
        if (escalate) {
            return BotOutcome.PASSED_ON;
        }
        if (topic != null) {
            return asksFeedback ? BotOutcome.ANSWERED : BotOutcome.SMALL_TALK;
        }
        if (!suggestions.isEmpty()) {
            return BotOutcome.SUGGESTED;
        }
        return unanswered ? BotOutcome.NOT_FOUND : BotOutcome.ASKED_AGAIN;
    }
}
