package com.irvinsegura.wallet.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Actuator on its own port, as in production: probes stay open for the orchestrator, metrics need the scrape
 * password Prometheus sends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "wallet.management.scrape-password=test-scrape-password")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ManagementPortIntegrationTest {

    // A fixed free port rather than 0: the security rules recognise the management port by its number
    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            registry.add("management.server.port", () -> port);
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalManagementPort
    private int managementPort;

    @Test
    void probesAreOpen() throws Exception {
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void metricsNeedTheScrapePassword() throws Exception {
        assertThat(get("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/prometheus", basic("prometheus", "wrong-password")).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/modulith", null).statusCode()).isEqualTo(401);

        HttpResponse<String> scraped = get("/actuator/prometheus", basic("prometheus", "test-scrape-password"));
        assertThat(scraped.statusCode()).isEqualTo(200);
        assertThat(scraped.body()).contains("jvm_memory_used_bytes");
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
