package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Sends one 1.00 MXN payout to Openpay's sandbox with the account's own keys, sends it again, and reads it back: the
 * real API taking it confirms the fields, the authentication, that a repeated {@code order_id} finds the payout
 * already made, and the lookup, which {@code FakeOpenpay} can only take on trust. It is never part of the build (the
 * name doesn't match the test pattern, and it needs {@code OPENPAY_SANDBOX_CHECK=true}); run it with
 * {@code ./mvnw test -Dtest=OpenpaySandboxCheck -Dsurefire.failIfNoSpecifiedTests=false}.
 *
 * <p>Takes {@code OPENPAY_MERCHANT_ID} and {@code OPENPAY_PRIVATE_KEY} from the sandbox dashboard, and optionally the
 * CLABE to pay ({@code OPENPAY_SANDBOX_CLABE}; a made-up valid one otherwise) and {@code OPENPAY_URL}.
 */
@EnabledIfEnvironmentVariable(named = "OPENPAY_SANDBOX_CHECK", matches = "true")
class OpenpaySandboxCheck {

    @Test
    void openpayTakesAPayoutOnceAndAnswersForIt() {
        OpenpayProperties properties = new OpenpayProperties(env("OPENPAY_URL", "https://sandbox-api.openpay.mx"),
                required("OPENPAY_MERCHANT_ID"), required("OPENPAY_PRIVATE_KEY"), "Prueba de integracion",
                ZoneId.of("America/Mexico_City"), Duration.ofSeconds(10), Duration.ofSeconds(30));
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        OpenpayPayoutGateway openpay = new OpenpayPayoutGateway(OpenpayPayoutGateway
                .authenticated(RestClient.builder().requestFactory(requestFactory), properties).build(), properties);
        PayoutOrder order = new PayoutOrder(UUID.randomUUID(), Money.ofMinor(100, "MXN"), Instant.now(),
                new PayoutOrder.Destination(env("OPENPAY_SANDBOX_CLABE", ApiClient.newClabe()), "Prueba Sandbox"));

        PayoutResult sent = send(openpay, order);
        System.out.println("Openpay took payout " + sent.reference() + ": " + sent.status() + " "
                + (sent.failureReason() == null ? "" : sent.failureReason()));
        assertThat(sent.status()).as("Openpay refused the payout: %s", sent.failureReason())
                .isNotEqualTo(PayoutResult.Status.FAILED);

        // The same withdrawal again, as after a lost answer: Openpay refuses the order_id and the lookup finds it
        PayoutResult again = send(openpay, order);
        System.out.println("Sent again, it is payout " + again.reference() + ": " + again.status());
        assertThat(again.reference()).as("A second payout was made for the same order").isEqualTo(sent.reference());

        PayoutResult state = openpay.check(sent.reference());
        System.out.println("Openpay says payout " + sent.reference() + " is " + state.status());
    }

    private static PayoutResult send(OpenpayPayoutGateway openpay, PayoutOrder order) {
        try {
            return openpay.send(order);
        } catch (PayoutProviderUnavailableException e) {
            // The log line above says what Openpay answered. 4001 means the sandbox account has no balance: make a
            // test card charge on the sandbox dashboard first. 1002 means the merchant id or key are wrong. 3006
            // means Openpay hasn't enabled payouts for the merchant: ask its support to
            return fail("Openpay did not take the payout; see the Openpay log line above for its answer");
        }
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
