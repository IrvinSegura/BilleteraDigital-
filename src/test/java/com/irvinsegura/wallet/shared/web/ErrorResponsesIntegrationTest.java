package com.irvinsegura.wallet.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Requests refused before any controller runs, by the security firewall or by Tomcat itself, through a real server:
 * MockMvc never goes through either.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ErrorResponsesIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @ParameterizedTest
    // A double slash, which the firewall rejects, and an encoded NUL, which Tomcat rejects before Spring sees it
    @ValueSource(strings = {"/api/v1/notifications//read", "/api/v1/wallets/%00"})
    void answerAsProblemDetailsWithoutEchoingThePath(String path) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"BAD_REQUEST\"").doesNotContain("/api/v1");
    }
}
