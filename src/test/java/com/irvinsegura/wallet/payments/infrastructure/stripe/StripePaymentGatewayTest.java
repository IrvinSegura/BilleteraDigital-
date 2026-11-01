package com.irvinsegura.wallet.payments.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.model.WebhookNotification;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StripePaymentGatewayTest {

    private static final String API = "https://api.stripe.test";
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RestClient.Builder builder = RestClient.builder().baseUrl(API);
    private final MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
    private final StripeSignatureVerifier verifier =
            new StripeSignatureVerifier("whsec_test", Duration.ofMinutes(5), clock);
    private final StripePaymentGateway gateway = new StripePaymentGateway(builder.build(), verifier,
            Duration.ofMinutes(30), "https://app.wallet.test/deposits/return", clock);

    private final UUID paymentId = UUID.randomUUID();
    private final Payment payment = Payment.register(paymentId, UUID.randomUUID(), Money.ofMinor(10050, "MXN"),
            "Deposit to your MXN wallet", StripePaymentGateway.NAME, NOW);

    @Test
    void opensACardCheckoutForTheExactAmount() {
        long expiresAt = NOW.plus(Duration.ofMinutes(30)).getEpochSecond();
        stripe.expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "checkout-" + paymentId))
                .andExpect(content().formDataContains(Map.of(
                        "mode", "payment",
                        "client_reference_id", paymentId.toString(),
                        "payment_method_types[0]", "card",
                        "line_items[0][price_data][currency]", "mxn",
                        "line_items[0][price_data][unit_amount]", "10050",
                        "line_items[0][price_data][product_data][name]", "Deposit to your MXN wallet",
                        "expires_at", Long.toString(expiresAt),
                        "success_url", "https://app.wallet.test/deposits/return?payment=" + paymentId
                                + "&result=success")))
                .andRespond(withSuccess("""
                        {"id": "cs_test_1", "object": "checkout.session", "url": "https://checkout.stripe.test/cs_test_1",
                         "status": "open", "payment_status": "unpaid", "expires_at": %d}""".formatted(expiresAt),
                        MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.openCheckout(payment);

        assertThat(session.reference()).isEqualTo("cs_test_1");
        assertThat(session.url()).isEqualTo("https://checkout.stripe.test/cs_test_1");
        assertThat(session.expiresAt()).isEqualTo(Instant.ofEpochSecond(expiresAt));
        assertThat(session.isPaid()).isFalse();
        stripe.verify();
    }

    @Test
    void readsAPaidCheckout() {
        stripe.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(session("complete", "paid"), MediaType.APPLICATION_JSON));

        CheckoutState state = gateway.checkCheckout("cs_test_1");

        assertThat(state.status()).isEqualTo(CheckoutState.Status.PAID);
        assertThat(state.paymentId()).isEqualTo(paymentId);
        assertThat(state.providerPaymentId()).isEqualTo("pi_test_1");
        assertThat(state.amount()).isEqualTo(Money.ofMinor(10050, "MXN"));
    }

    @Test
    void aCheckoutStripeWillNotExpireIsReadInstead() {
        stripe.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1/expire"))
                .andRespond(withBadRequest().body("{\"error\": {\"message\": \"Only open sessions can be expired\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        stripe.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1"))
                .andRespond(withSuccess(session("complete", "paid"), MediaType.APPLICATION_JSON));

        assertThat(gateway.expireCheckout("cs_test_1").status()).isEqualTo(CheckoutState.Status.PAID);
    }

    @Test
    void refundsThePaymentIntentOnce() {
        Payment paid = Payment.restore(paymentId, payment.userId(), payment.amount(), payment.description(),
                StripePaymentGateway.NAME, PaymentStatus.REFUND_PENDING, "cs_test_1", null, null, "pi_test_1", null,
                NOW, NOW, 1L);
        stripe.expect(requestTo(API + "/v1/refunds"))
                .andExpect(header("Idempotency-Key", "refund-" + paymentId))
                .andExpect(content().formDataContains(Map.of("payment_intent", "pi_test_1")))
                .andRespond(withSuccess("{\"id\": \"re_test_1\", \"object\": \"refund\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.refund(paid)).isEqualTo("re_test_1");
    }

    @Test
    void anErrorFromStripeMeansTheProviderIsUnavailable() {
        stripe.expect(requestTo(API + "/v1/checkout/sessions")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.openCheckout(payment))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void readsACompletedCheckoutWebhook() {
        String payload = """
                {"id": "evt_1", "type": "checkout.session.completed", "data": {"object": %s}}"""
                .formatted(session("complete", "paid"));

        WebhookNotification notification = gateway.readWebhook(payload, verifier.header(payload,
                NOW.getEpochSecond()));

        assertThat(notification.eventId()).isEqualTo("evt_1");
        assertThat(notification.checkout().status()).isEqualTo(CheckoutState.Status.PAID);
        assertThat(notification.checkout().paymentId()).isEqualTo(paymentId);
    }

    @Test
    void acknowledgesOtherEventTypesWithoutReadingThem() {
        // A charge object has fields of other shapes; it must never be read as a checkout session
        String payload = """
                {"id": "evt_2", "type": "charge.succeeded", "data": {"object": {"id": "ch_1", "amount_total": "x"}}}""";

        WebhookNotification notification = gateway.readWebhook(payload, verifier.header(payload,
                NOW.getEpochSecond()));

        assertThat(notification.type()).isEqualTo("charge.succeeded");
        assertThat(notification.checkout()).isNull();
    }

    @Test
    void rejectsUnsignedOrUnreadableWebhooks() {
        String payload = "{\"id\": \"evt_3\", \"type\": \"checkout.session.completed\"}";

        assertThatThrownBy(() -> gateway.readWebhook(payload, null)).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> gateway.readWebhook(payload, verifier.header(payload, NOW.getEpochSecond())))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> gateway.readWebhook("not json", verifier.header("not json", NOW.getEpochSecond())))
                .isInstanceOf(InvalidWebhookException.class);
    }

    private String session(String status, String paymentStatus) {
        return """
                {"id": "cs_test_1", "object": "checkout.session", "status": "%s", "payment_status": "%s",
                 "payment_intent": "pi_test_1", "client_reference_id": "%s", "amount_total": 10050,
                 "currency": "mxn"}""".formatted(status, paymentStatus, paymentId);
    }
}
