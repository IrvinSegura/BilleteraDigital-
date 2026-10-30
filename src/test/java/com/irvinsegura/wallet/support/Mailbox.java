package com.irvinsegura.wallet.support;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * Reads the emails Mailpit received, through its HTTP API.
 */
public final class Mailbox {

    private final RestClient client;

    public Mailbox(MailpitContainer mailpit) {
        this.client = RestClient.create(mailpit.apiUrl());
    }

    /**
     * Subjects of the emails sent to that address, newest first.
     */
    public List<String> subjectsFor(String email) {
        return JsonPath.read(search(email), "$.messages[*].Subject");
    }

    /**
     * Beginning of each email's text, newest first.
     */
    public List<String> snippetsFor(String email) {
        return JsonPath.read(search(email), "$.messages[*].Snippet");
    }

    private String search(String email) {
        return client.get()
                .uri("/api/v1/search?query={query}", "to:\"" + email + "\"")
                .retrieve()
                .body(String.class);
    }
}
