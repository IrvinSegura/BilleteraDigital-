package com.irvinsegura.wallet.support;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;

/**
 * Reads the emails Mailpit received, through its HTTP API.
 */
public final class Mailbox {

    // Account emails put the token in the link's fragment: https://app.../verify-email#token=...
    private static final Pattern LINK_TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final RestClient client;

    public Mailbox(MailpitContainer mailpit) {
        this.client = RestClient.create(mailpit.apiUrl());
    }

    /**
     * Subjects of the emails sent to that address, newest first.
     */
    public List<String> subjectsFor(String email) {
        return JsonPath.read(search("to:\"" + email + "\""), "$.messages[*].Subject");
    }

    /**
     * Beginning of each email's text, newest first.
     */
    public List<String> snippetsFor(String email) {
        return JsonPath.read(search("to:\"" + email + "\""), "$.messages[*].Snippet");
    }

    /**
     * Tokens of the links in the emails with that subject sent to that address, newest first.
     */
    public List<String> linkTokensFor(String email, String subject) {
        List<String> ids = JsonPath.read(search("to:\"" + email + "\" subject:\"" + subject + "\""),
                "$.messages[*].ID");
        return ids.stream().map(this::linkToken).toList();
    }

    /**
     * Text of the emails with that subject sent to that address, newest first.
     */
    public List<String> textsFor(String email, String subject) {
        List<String> ids = JsonPath.read(search("to:\"" + email + "\" subject:\"" + subject + "\""),
                "$.messages[*].ID");
        return ids.stream().map(this::text).toList();
    }

    private String linkToken(String messageId) {
        Matcher matcher = LINK_TOKEN.matcher(text(messageId));
        if (!matcher.find()) {
            throw new AssertionError("No link token in email " + messageId);
        }
        return matcher.group(1);
    }

    private String text(String messageId) {
        return JsonPath.read(client.get().uri("/api/v1/message/{id}", messageId).retrieve().body(String.class),
                "$.Text");
    }

    private String search(String query) {
        return client.get()
                .uri("/api/v1/search?query={query}", query)
                .retrieve()
                .body(String.class);
    }
}
