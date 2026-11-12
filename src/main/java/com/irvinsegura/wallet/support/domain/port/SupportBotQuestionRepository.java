package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotFeedback;
import com.irvinsegura.wallet.support.domain.model.BotMissKind;
import com.irvinsegura.wallet.support.domain.model.BotQuestion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the bot made of each question, for administrators to see where it fails.
 */
public interface SupportBotQuestionRepository {

    void add(BotQuestion question);

    /**
     * Records what the user said about the last question of the conversation, if the bot answered it.
     */
    void recordFeedback(UUID conversationId, BotFeedback feedback);

    BotSummary summarize(Instant since);

    /**
     * The same question asked the same way counts once, with how many times: the most repeated first.
     *
     * @param language {@code null} for both
     */
    BotMissSlice findMisses(List<BotMissKind> kinds, Language language, Instant since, int page, int size);

    /**
     * @param questions what the bot was asked, greetings and thanks left out
     * @param answered  answers the user could say helped or not
     */
    record BotSummary(long questions, long answered, long helped, long notHelped, long passedOn, long suggested,
            long askedAgain, long notFound) {
    }

    /**
     * @param question       how it was written the last time
     * @param topic          the answer that didn't help or the ones offered, one per line; {@code null} otherwise
     * @param conversationId the conversation where it was asked the last time
     */
    record BotMiss(BotMissKind kind, Language language, String question, String topic, long times,
            Instant lastAskedAt, UUID conversationId) {
    }

    record BotMissSlice(List<BotMiss> items, boolean hasNext) {
    }
}
