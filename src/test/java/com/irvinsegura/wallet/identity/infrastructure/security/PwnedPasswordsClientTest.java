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

class PwnedPasswordsClientTest {

    // SHA-1 of "password" is 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "";
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath() + " padding=" + exchange.getRequestHeaders().getFirst(
                    "Add-Padding"));
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
    void sendsOnlyTheHashPrefixAndFindsTheSuffix() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + SUFFIX + ":10434004\r\n";

        assertThat(client(Duration.ofSeconds(2)).isBreached("password")).isTrue();
        assertThat(requests).containsExactly("/range/" + PREFIX + " padding=true");
    }

    @Test
    void anAbsentSuffixIsNotBreached() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n";

        assertThat(client(Duration.ofSeconds(2)).isBreached("password")).isFalse();
    }

    @Test
    void paddingLinesDoNotCount() {
        // Padding entries are made-up suffixes with a count of 0
        body = SUFFIX + ":0\r\n";

        assertThat(client(Duration.ofSeconds(2)).isBreached("password")).isFalse();
    }

    @Test
    void anErrorLetsThePasswordThrough() {
        status = 503;
        body = SUFFIX + ":10";

        assertThat(client(Duration.ofSeconds(2)).isBreached("password")).isFalse();
    }

    @Test
    void aSlowAnswerLetsThePasswordThrough() {
        delayMillis = 1_000;
        body = SUFFIX + ":10";

        assertThat(client(Duration.ofMillis(200)).isBreached("password")).isFalse();
    }

    @Test
    void anUnreachableServiceLetsThePasswordThrough() {
        server.stop(0);

        assertThat(client(Duration.ofMillis(500)).isBreached("password")).isFalse();
    }

    private PwnedPasswordsClient client(Duration timeout) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new PwnedPasswordsClient(baseUrl, timeout);
    }
}
