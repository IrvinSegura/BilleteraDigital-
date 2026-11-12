package com.irvinsegura.wallet.support.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads the Markdown files the bot answers from. Each {@code ##} heading is one answer: the question as people would
 * ask it, followed by optional lines of its own and then the text.
 *
 * <pre>
 * ## How do I withdraw money?
 * keywords: withdraw, take out, send to my bank
 * escalate: true
 * small-talk: true
 *
 * The answer, as many paragraphs as it needs.
 * </pre>
 *
 * {@code escalate} also hands the conversation to a person, for what the bot can explain but not solve, and
 * {@code small-talk} is for greetings and thanks, which aren't answers to rate. A section with {@code reply: unclear},
 * {@code reply: suggestions} or {@code reply: not-found} is no answer: it is what the bot says when it finds none, see
 * {@link KnowledgeIndex}. The keywords of {@code not-found} are the words of someone reporting a problem. The text of
 * {@code reply: synonyms} has a line per word the sections use and the ones people may use for it, as
 * {@code dinero: plata, lana}.
 */
public final class KnowledgeDocument {

    private static final String SECTION = "## ";
    private static final String TOPIC = "# ";

    private KnowledgeDocument() {
    }

    /**
     * @throws IllegalArgumentException if a heading has no text: better to find out when the app starts
     */
    public static List<KnowledgeSection> parse(String markdown) {
        List<KnowledgeSection> sections = new ArrayList<>();
        String title = null;
        List<String> lines = new ArrayList<>();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith(SECTION)) {
                if (title != null) {
                    sections.add(section(title, lines));
                }
                title = line.substring(SECTION.length()).strip();
                lines = new ArrayList<>();
            } else if (title != null && !line.startsWith(TOPIC)) {
                lines.add(line);
            }
        }
        if (title != null) {
            sections.add(section(title, lines));
        }
        return sections;
    }

    private static KnowledgeSection section(String title, List<String> lines) {
        List<String> keywords = List.of();
        boolean escalate = false;
        boolean smallTalk = false;
        String reply = null;
        int first = 0;
        for (; first < lines.size(); first++) {
            String line = lines.get(first).strip();
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("keywords:")) {
                keywords = Arrays.stream(line.substring("keywords:".length()).split(","))
                        .map(String::strip).filter(keyword -> !keyword.isEmpty()).toList();
            } else if (lower.equals("escalate: true")) {
                escalate = true;
            } else if (lower.equals("small-talk: true")) {
                smallTalk = true;
            } else if (lower.startsWith("reply:")) {
                reply = lower.substring("reply:".length()).strip();
            } else if (!line.isEmpty()) {
                break;
            }
        }
        String body = String.join("\n", lines.subList(first, lines.size())).strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Section without text: " + title);
        }
        return new KnowledgeSection(title, keywords, body, escalate, smallTalk, reply);
    }
}
