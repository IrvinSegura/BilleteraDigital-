package com.irvinsegura.wallet.support.infrastructure.knowledge;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotReply;
import com.irvinsegura.wallet.support.domain.model.KnowledgeDocument;
import com.irvinsegura.wallet.support.domain.model.KnowledgeIndex;
import com.irvinsegura.wallet.support.domain.model.KnowledgeSection;
import com.irvinsegura.wallet.support.domain.port.SupportKnowledge;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Answers from the Markdown files in {@code support/knowledge/<language>/}, read when the app starts: changing what
 * the bot knows is editing a file and deploying. A language without any answers stops the app from starting.
 */
@Component
class MarkdownSupportKnowledge implements SupportKnowledge {

    private final Map<Language, KnowledgeIndex> indexes = new EnumMap<>(Language.class);

    MarkdownSupportKnowledge() {
        for (Language language : Language.values()) {
            indexes.put(language, new KnowledgeIndex(load(language)));
        }
    }

    @Override
    public BotReply reply(String question, Language language) {
        return indexes.get(language).reply(question);
    }

    // The section that answers the question, if any
    Optional<KnowledgeSection> answer(String question, Language language) {
        return indexes.get(language).find(question);
    }

    private static List<KnowledgeSection> load(Language language) {
        List<KnowledgeSection> sections = new ArrayList<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:support/knowledge/" + language.tag() + "/*.md");
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    sections.addAll(KnowledgeDocument.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("The support bot has nothing to answer with in " + language.tag());
        }
        return sections;
    }
}
