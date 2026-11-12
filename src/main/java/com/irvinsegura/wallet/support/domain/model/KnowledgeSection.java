package com.irvinsegura.wallet.support.domain.model;

import java.util.List;

/**
 * One answer of the bot, see {@link KnowledgeDocument}.
 *
 * @param reply when it isn't an answer to a question but what the bot says in that situation, see {@link KnowledgeIndex}
 */
public record KnowledgeSection(String title, List<String> keywords, String body, boolean escalate, boolean smallTalk,
        String reply) {

    public KnowledgeSection {
        keywords = List.copyOf(keywords);
    }
}
