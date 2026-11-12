package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * MXN payouts to a CLABE through Openpay ({@code POST /v1/{merchant}/payouts}). Each payout carries the withdrawal's
 * id as its {@code order_id}, which Openpay takes once ({@code 409}, error 1006, for a second one): an attempt whose
 * answer was lost finds the payout already made instead of paying twice. Its state is then read by id on every run of
 * the payout job until Openpay reports it completed or failed.
 * <p>
 * Calls authenticate with HTTP Basic, the private key as the user and no password, as Openpay's API reference says.
 */
public class OpenpayPayoutGateway implements PayoutGateway {

    static final String CURRENCY_NOT_SUPPORTED = "CURRENCY_NOT_SUPPORTED";

    private static final Logger log = LoggerFactory.getLogger(OpenpayPayoutGateway.class);
    private static final String PESOS = "MXN";
    private static final int DUPLICATE_ORDER = 1006;
    // Not a refusal of this payout: the platform's Openpay balance is short, the keys are wrong, the merchant may not
    // make payouts yet (3006, until Openpay enables them) or Openpay failed
    private static final Set<Integer> NOT_THIS_PAYOUT = Set.of(1000, 1002, 1004, 1010, 1012, 3006, 4001);
    private static final int MAX_DESCRIPTION = 250;
    private static final int LISTED_PER_PAGE = 100;

    private final RestClient client;
    private final OpenpayProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OpenpayPayoutGateway(RestClient client, OpenpayProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * Builds the client with Openpay's authentication: every call carries the private key.
     */
    static RestClient.Builder authenticated(RestClient.Builder builder, OpenpayProperties properties) {
        String credentials = Base64.getEncoder()
                .encodeToString((properties.privateKey() + ":").getBytes(StandardCharsets.UTF_8));
        return builder.baseUrl(properties.url() + "/v1/" + properties.merchantId())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
    }

    // Payouts go only to Mexican bank accounts, in pesos
    @Override
    public boolean pays(Currency currency) {
        return PESOS.equals(currency.getCurrencyCode());
    }

    @Override
    public PayoutResult send(PayoutOrder order) {
        String orderId = order.withdrawalId().toString();
        if (order.destination() == null || !pays(order.amount().currency())) {
            // Never reaches here: withdrawals in other currencies are refused before they are made
            return PayoutResult.failed(orderId, CURRENCY_NOT_SUPPORTED);
        }
        Map<String, Object> bankAccount = new LinkedHashMap<>();
        bankAccount.put("clabe", order.destination().clabe());
        bankAccount.put("holder_name", order.destination().holderName());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method", "bank_account");
        body.put("bank_account", bankAccount);
        body.put("amount", order.amount().toDecimal().setScale(2, RoundingMode.UNNECESSARY));
        body.put("description", description());
        body.put("order_id", orderId);

        Answer answer = call("send the payout of withdrawal " + orderId, () -> client.post()
                .uri("/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> new Answer(response.getStatusCode().value(), text(response))));
        if (answer.isSuccess()) {
            return toResult(read(answer.body(), Payout.class));
        }
        OpenpayError error = readError(answer);
        if (error.code() == DUPLICATE_ORDER) {
            // Made before, by an attempt whose answer was lost: that payout is followed instead
            LocalDate day = LocalDate.ofInstant(order.requestedAt(), properties.timeZone());
            return findByOrderId(orderId, day).map(OpenpayPayoutGateway::toResult).orElseThrow(() -> {
                log.error("Openpay has a payout for withdrawal {} that the lookup didn't find", orderId);
                return new PayoutProviderUnavailableException();
            });
        }
        if (answer.status() >= 500 || NOT_THIS_PAYOUT.contains(error.code())) {
            // Giving the money back could pay it twice or refuse a payout that only has to wait: it stays pending,
            // is sent again and the stuck alert fires if it never goes through
            log.error("Openpay did not take the payout of withdrawal {}: {} {}", orderId, error.code(),
                    error.description());
            throw new PayoutProviderUnavailableException();
        }
        log.warn("Openpay refused the payout of withdrawal {} ({}): {}", orderId, error.code(), error.description());
        return PayoutResult.failed(orderId, "OPENPAY_REJECTED_" + error.code());
    }

    @Override
    public PayoutResult check(String reference) {
        Answer answer = call("look up the payout " + reference, () -> client.get()
                .uri("/payouts/{id}", reference)
                .exchange((request, response) -> new Answer(response.getStatusCode().value(), text(response))));
        if (answer.isSuccess()) {
            return toResult(read(answer.body(), Payout.class));
        }
        // A payout sent by another provider before the switch to Openpay is not found: kept pending (the stuck alert
        // fires) instead of stopping the payout job's whole batch
        OpenpayError error = readError(answer);
        log.error("Openpay could not look up the payout {}: {} {}", reference, error.code(), error.description());
        throw new PayoutProviderUnavailableException();
    }

    private Optional<Payout> findByOrderId(String orderId, LocalDate day) {
        // From the day before, in case Openpay dated it in another time zone
        String from = day.minusDays(1).toString();
        Answer answer = call("look for the payout of withdrawal " + orderId, () -> client.get()
                .uri(uri -> uri.path("/payouts")
                        .queryParam("creation[gte]", from)
                        .queryParam("limit", LISTED_PER_PAGE)
                        .build())
                .exchange((request, response) -> new Answer(response.getStatusCode().value(), text(response))));
        if (!answer.isSuccess()) {
            throw new PayoutProviderUnavailableException();
        }
        return List.of(read(answer.body(), Payout[].class)).stream()
                .filter(payout -> orderId.equals(payout.orderId()))
                .findFirst();
    }

    private static PayoutResult toResult(Payout payout) {
        String status = payout.status() == null ? "" : payout.status().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "completed" -> PayoutResult.paid(payout.id());
            case "failed", "cancelled" -> PayoutResult.failed(payout.id(), "OPENPAY_" + status.toUpperCase(Locale.ROOT));
            default -> PayoutResult.pending(payout.id());
        };
    }

    private String description() {
        String concept = properties.concept();
        return concept.length() > MAX_DESCRIPTION ? concept.substring(0, MAX_DESCRIPTION) : concept;
    }

    private Answer call(String action, Supplier<Answer> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("Could not reach Openpay to {}: {}", action, e.getMessage());
            throw new PayoutProviderUnavailableException();
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return jsonMapper.readValue(body, type);
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("Unreadable answer from Openpay");
            throw new PayoutProviderUnavailableException();
        }
    }

    private OpenpayError readError(Answer answer) {
        try {
            return jsonMapper.readValue(answer.body(), OpenpayError.class);
        } catch (JacksonException | IllegalArgumentException e) {
            return new OpenpayError(0, "HTTP " + answer.status());
        }
    }

    private static String text(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private record Answer(int status, String body) {

        boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * @param status {@code in_progress} until the bank pays it, then {@code completed} or {@code failed}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payout(String id, String status, @JsonProperty("order_id") String orderId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenpayError(@JsonProperty("error_code") int code, String description) {
    }
}
