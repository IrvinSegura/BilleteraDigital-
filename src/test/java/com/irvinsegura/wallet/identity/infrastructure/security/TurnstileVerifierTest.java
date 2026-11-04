package com.irvinsegura.wallet.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurnstileVerifierTest {

    private HttpServer server;
    private final List<String> forms = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{\"success\": true}";
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            forms.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsTheSecretTokenAndAddress() {
        assertThat(verifier(Duration.ofSeconds(2)).isHuman("token+1", "203.0.113.7")).isTrue();

        assertThat(forms).containsExactly("secret=test-secret&response=token%2B1&remoteip=203.0.113.7");
    }

    @Test
    void aRefusedTokenFails() {
        body = "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}";

        assertThat(verifier(Duration.ofSeconds(2)).isHuman("used-token", null)).isFalse();
    }

    @Test
    void aMissingOrOversizedTokenFailsWithoutAsking() {
        assertThat(verifier(Duration.ofSeconds(2)).isHuman(null, null)).isFalse();
        assertThat(verifier(Duration.ofSeconds(2)).isHuman(" ", null)).isFalse();
        assertThat(verifier(Duration.ofSeconds(2)).isHuman("x".repeat(2049), null)).isFalse();
        assertThat(forms).isEmpty();
    }

    @Test
    void anOutageFailsClosed() {
        status = 500;
        assertThat(verifier(Duration.ofSeconds(2)).isHuman("token", null)).isFalse();

        status = 200;
        delayMillis = 1_000;
        assertThat(verifier(Duration.ofMillis(200)).isHuman("token", null)).isFalse();

        server.stop(0);
        assertThat(verifier(Duration.ofMillis(500)).isHuman("token", null)).isFalse();
    }

    private TurnstileVerifier verifier(Duration timeout) {
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
        return new TurnstileVerifier(url, "test-secret", timeout);
    }
}
