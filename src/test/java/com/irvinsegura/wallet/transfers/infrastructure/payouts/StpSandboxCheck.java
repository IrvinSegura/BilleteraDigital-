package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Sends one real 1.00 MXN order to STP's demo with the account's own credentials and reads it back: STP accepting it
 * confirms the signed fields, their order and the query, which {@code FakeStp} can only take on trust. It is never
 * part of the build (the name doesn't match the test pattern, and it needs {@code STP_SANDBOX_CHECK=true}); run it with
 * {@code ./mvnw test -Dtest=StpSandboxCheck -Dsurefire.failIfNoSpecifiedTests=false}.
 *
 * <p>Takes the same settings as the app ({@code STP_COMPANY}, {@code STP_ACCOUNT}, {@code STP_ACCOUNT_HOLDER},
 * {@code STP_ACCOUNT_HOLDER_RFC}, and {@code STP_ORDERS_URL} / {@code STP_QUERY_URL} for other than the demo), the key
 * as a file ({@code STP_PRIVATE_KEY_FILE}) or as text ({@code STP_PRIVATE_KEY}), and the CLABE to pay
 * ({@code STP_SANDBOX_CLABE}, one STP's demo accepts).
 */
@EnabledIfEnvironmentVariable(named = "STP_SANDBOX_CHECK", matches = "true")
class StpSandboxCheck {

    private static final Duration WAIT_FOR_ORDER = Duration.ofSeconds(60);

    @Test
    void stpTakesAnOrderAndAnswersForIt() throws Exception {
        StpProperties properties = new StpProperties(
                env("STP_ORDERS_URL", "https://demo.stpmex.com:7024/speiws/rest"),
                env("STP_QUERY_URL", "https://efws-dev.stpmex.com/efws/API"),
                required("STP_COMPANY"), required("STP_ACCOUNT"), required("STP_ACCOUNT_HOLDER"),
                required("STP_ACCOUNT_HOLDER_RFC"), privateKey(), "Prueba de integracion",
                ZoneId.of("America/Mexico_City"), 1, Duration.ofSeconds(10), Duration.ofSeconds(30));
        StpPayoutGateway stp = new StpPayoutGateway(client(properties.ordersUrl()), client(properties.queryUrl()),
                StpSigner.fromPem(properties.privateKey()), properties, Clock.systemUTC());
        PayoutOrder order = new PayoutOrder(UUID.randomUUID(), Money.ofMinor(100, "MXN"),
                Instant.now(), new PayoutOrder.Destination(required("STP_SANDBOX_CLABE"), "Prueba Sandbox"));

        PayoutResult sent;
        try {
            sent = stp.send(order);
        } catch (PayoutProviderUnavailableException e) {
            // The log line above says what STP answered: an invalid signature or a missing field shows up here
            fail("STP did not take the order; see the STP log line above for its answer");
            return;
        }
        System.out.println("STP took order " + sent.reference() + ": " + sent.status() + " "
                + (sent.failureReason() == null ? "" : sent.failureReason()));
        assertThat(sent.status()).as("STP refused the order: %s", sent.failureReason())
                .isNotEqualTo(PayoutResult.Status.FAILED);

        // The query is signed too: an answer of any kind proves it, and the order shows up once STP has it
        Instant deadline = Instant.now().plus(WAIT_FOR_ORDER);
        PayoutResult state;
        do {
            Thread.sleep(5_000);
            state = stp.check(sent.reference());
            System.out.println("STP says order " + sent.reference() + " is " + state.status());
        } while (state.status() == PayoutResult.Status.PENDING && Instant.now().isBefore(deadline));
        System.out.println("If every line above says PENDING and the log says STP has no order with that tracking "
                + "key, the order was taken but the query service (STP_QUERY_URL) does not find it: check that URL "
                + "and its fields with STP.");
    }

    private static RestClient client(String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private static String privateKey() throws IOException {
        String file = System.getenv("STP_PRIVATE_KEY_FILE");
        return file == null || file.isBlank() ? required("STP_PRIVATE_KEY") : Files.readString(Path.of(file));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " is not set");
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
