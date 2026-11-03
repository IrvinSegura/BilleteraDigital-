package com.irvinsegura.wallet.payments.infrastructure.stripe;

import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalance;
import com.irvinsegura.wallet.payments.domain.model.WebhookNotification;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.Balance;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.BalanceTransactionList;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.BalanceTransactionObject;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.CheckoutSessionObject;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.Event;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.EventEnvelope;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeObjects.Refund;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stripe Checkout through Stripe's REST API. Four form-encoded calls do not justify the official SDK; the webhook
 * signature check it would provide is {@link StripeSignatureVerifier}.
 * <p>
 * Only cards are offered: they are confirmed while the payer waits, so a completed checkout is always paid.
 * Vouchers and bank transfers would add a "complete but unpaid" state that can last days.
 */
public class StripePaymentGateway implements PaymentGateway {

    public static final String NAME = "STRIPE";

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    // Stripe's largest page
    private static final int BALANCE_PAGE_SIZE = 100;
    // Webhook types that settle a checkout; every other type is acknowledged and ignored
    private static final Set<String> CHECKOUT_EVENTS = Set.of("checkout.session.completed",
            "checkout.session.async_payment_succeeded", "checkout.session.expired");

    private final RestClient restClient;
    private final StripeSignatureVerifier signatureVerifier;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Duration checkoutTtl;
    private final String returnUrl;
    private final Clock clock;

    public StripePaymentGateway(RestClient restClient, StripeSignatureVerifier signatureVerifier,
            Duration checkoutTtl, String returnUrl, Clock clock) {
        this.restClient = restClient;
        this.signatureVerifier = signatureVerifier;
        this.checkoutTtl = checkoutTtl;
        this.returnUrl = returnUrl;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean holdsFunds() {
        return true;
    }

    @Override
    public CheckoutSession openCheckout(Payment payment) {
        Instant expiresAt = clock.instant().plus(checkoutTtl).truncatedTo(ChronoUnit.SECONDS);
        String paymentId = payment.id().toString();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", paymentId);
        form.add("metadata[payment_id]", paymentId);
        form.add("payment_intent_data[metadata][payment_id]", paymentId);
        form.add("payment_method_types[0]", "card");
        form.add("line_items[0][quantity]", "1");
        // MXN and USD use two decimals in Stripe too, so minor units are sent as they are
        form.add("line_items[0][price_data][currency]",
                payment.amount().currency().getCurrencyCode().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(payment.amount().minorUnits()));
        form.add("line_items[0][price_data][product_data][name]", payment.description());
        form.add("success_url", returnUrl(paymentId, "success"));
        form.add("cancel_url", returnUrl(paymentId, "cancel"));
        form.add("expires_at", Long.toString(expiresAt.getEpochSecond()));
        CheckoutSessionObject session = call("open checkout for payment " + paymentId, () -> restClient.post()
                .uri("/v1/checkout/sessions")
                // A retry after a timeout returns the session created the first time instead of opening another
                .header("Idempotency-Key", "checkout-" + paymentId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class), CheckoutSessionObject.class);
        return new CheckoutSession(session.id(), session.url(), Instant.ofEpochSecond(session.expiresAt()), null);
    }

    @Override
    public CheckoutState checkCheckout(String reference) {
        return toState(call("check checkout " + reference, () -> restClient.get()
                .uri("/v1/checkout/sessions/{id}", reference)
                .retrieve()
                .body(String.class), CheckoutSessionObject.class));
    }

    @Override
    public CheckoutState expireCheckout(String reference) {
        try {
            return toState(call("expire checkout " + reference, () -> restClient.post()
                    .uri("/v1/checkout/sessions/{id}/expire", reference)
                    .retrieve()
                    .body(String.class), CheckoutSessionObject.class));
        } catch (ClientError e) {
            // Stripe refuses to expire a session that is no longer open, e.g. it was paid a moment ago
            return checkCheckout(reference);
        }
    }

    @Override
    public String refund(Payment payment) {
        String paymentId = payment.id().toString();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", payment.providerPaymentId());
        form.add("metadata[payment_id]", paymentId);
        return call("refund payment " + paymentId, () -> restClient.post()
                .uri("/v1/refunds")
                .header("Idempotency-Key", "refund-" + paymentId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class), Refund.class).id();
    }

    @Override
    public WebhookNotification readWebhook(String payload, String signature) {
        signatureVerifier.verify(payload, signature);
        try {
            EventEnvelope envelope = jsonMapper.readValue(payload, EventEnvelope.class);
            if (envelope.id() == null || envelope.type() == null) {
                throw new InvalidWebhookException("Event without id or type");
            }
            if (!CHECKOUT_EVENTS.contains(envelope.type())) {
                return new WebhookNotification(envelope.id(), envelope.type(), null);
            }
            Event event = jsonMapper.readValue(payload, Event.class);
            if (event.data() == null || event.data().object() == null) {
                throw new InvalidWebhookException("Checkout event without a session");
            }
            return new WebhookNotification(event.id(), event.type(), toState(event.data().object()));
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Unreadable event");
        }
    }

    @Override
    public List<BalanceTransaction> balanceTransactionsSince(Instant since) {
        List<BalanceTransaction> transactions = new ArrayList<>();
        String startingAfter = null;
        boolean hasMore = true;
        // Stripe lists newest first, a page at a time; the whole window is read before anything is recorded
        while (hasMore) {
            String after = startingAfter;
            BalanceTransactionList page = call("list balance transactions", () -> restClient.get()
                    .uri(uri -> {
                        uri.path("/v1/balance_transactions")
                                .queryParam("limit", BALANCE_PAGE_SIZE)
                                .queryParam("created[gte]", since.getEpochSecond())
                                .queryParam("expand[]", "data.source");
                        if (after != null) {
                            uri.queryParam("starting_after", after);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(String.class), BalanceTransactionList.class);
            List<BalanceTransactionObject> data = page.data() == null ? List.of() : page.data();
            data.forEach(transaction -> transactions.add(toBalanceTransaction(transaction)));
            hasMore = page.hasMore() && !data.isEmpty();
            startingAfter = data.isEmpty() ? null : data.getLast().id();
        }
        transactions.sort(Comparator.comparing(BalanceTransaction::createdAt).thenComparing(BalanceTransaction::id));
        return transactions;
    }

    @Override
    public List<ProviderBalance> balance() {
        Balance balance = call("read the balance", () -> restClient.get()
                .uri("/v1/balance")
                .retrieve()
                .body(String.class), Balance.class);
        Map<String, Long> totals = new TreeMap<>();
        Stream.of(balance.available(), balance.pending())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach(amount -> totals.merge(amount.currency().toUpperCase(Locale.ROOT), amount.amount(),
                        Long::sum));
        return totals.entrySet().stream().map(total -> new ProviderBalance(total.getKey(), total.getValue())).toList();
    }

    private static BalanceTransaction toBalanceTransaction(BalanceTransactionObject transaction) {
        BalanceTransaction.Type type = switch (transaction.type()) {
            case "charge", "payment" -> BalanceTransaction.Type.CHARGE;
            case "refund", "payment_refund" -> BalanceTransaction.Type.REFUND;
            case "payout" -> BalanceTransaction.Type.PAYOUT;
            case "payout_failure", "payout_cancel" -> BalanceTransaction.Type.PAYOUT_REVERSAL;
            case "stripe_fee" -> BalanceTransaction.Type.FEE;
            case null, default -> BalanceTransaction.Type.OTHER;
        };
        // Charges and refunds carry their PaymentIntent, which is what a payment stores
        String paymentIntent = transaction.source() instanceof Map<?, ?> source
                && source.get("payment_intent") instanceof String id ? id : null;
        return new BalanceTransaction(transaction.id(), type, String.valueOf(transaction.type()),
                transaction.amount(), transaction.fee(), transaction.currency().toUpperCase(Locale.ROOT),
                paymentIntent, Instant.ofEpochSecond(transaction.created()));
    }

    private String returnUrl(String paymentId, String result) {
        return UriComponentsBuilder.fromUriString(returnUrl)
                .queryParam("payment", paymentId)
                .queryParam("result", result)
                .toUriString();
    }

    private CheckoutState toState(CheckoutSessionObject session) {
        CheckoutState.Status status;
        if ("paid".equals(session.paymentStatus())) {
            status = CheckoutState.Status.PAID;
        } else if ("expired".equals(session.status())) {
            status = CheckoutState.Status.EXPIRED;
        } else {
            status = CheckoutState.Status.OPEN;
        }
        Money amount = session.amountTotal() == null || session.currency() == null ? null
                : Money.ofMinor(session.amountTotal(), session.currency().toUpperCase(Locale.ROOT));
        return new CheckoutState(parsePaymentId(session.clientReferenceId()), session.id(), status,
                session.paymentIntent(), amount);
    }

    private static UUID parsePaymentId(String clientReferenceId) {
        try {
            return clientReferenceId == null ? null : UUID.fromString(clientReferenceId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Any failure to get an answer becomes {@link PaymentProviderUnavailableException}; callers retry later or tell
     * the user to. Stripe's error message is logged, the API key never is (it travels in a default header).
     */
    private <T> T call(String action, Supplier<String> request, Class<T> type) {
        String body;
        try {
            body = request.get();
        } catch (HttpClientErrorException e) {
            log.warn("Stripe rejected the request to {}: {} {}", action, e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new ClientError();
        } catch (HttpStatusCodeException e) {
            log.warn("Stripe failed to {}: {}", action, e.getStatusCode());
            throw new PaymentProviderUnavailableException();
        } catch (RestClientException e) {
            log.warn("Could not reach Stripe to {}: {}", action, e.getMessage());
            throw new PaymentProviderUnavailableException();
        }
        try {
            if (body == null) {
                throw new PaymentProviderUnavailableException();
            }
            return jsonMapper.readValue(body, type);
        } catch (JacksonException e) {
            log.warn("Unreadable answer from Stripe to {}", action);
            throw new PaymentProviderUnavailableException();
        }
    }

    // A 4xx answer: the request itself is wrong, so retrying it unchanged cannot help
    private static final class ClientError extends PaymentProviderUnavailableException {
    }
}
