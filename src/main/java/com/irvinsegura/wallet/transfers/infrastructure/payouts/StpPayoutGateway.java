package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * MXN payouts by SPEI through STP. A payout is registered as an order ({@code ordenPago/registra}) and its state is
 * then read by tracking key ({@code consultaOrden}) on every run of the payout job, until STP reports it settled or
 * returned by the receiving bank.
 * <p>
 * The tracking key comes from the withdrawal, so every attempt names the same order. STP refuses a repeated key only
 * within one operation day, so before registering, and whenever STP refuses an order, the order is looked for on the
 * days it may have been registered: a payout STP already has is never registered twice.
 * <p>
 * Field names, the order of the 34 signed fields, the lengths and the state codes match STP's SpeiWS documentation
 * as public STP clients implement it (stpmex for Python, go-stp). {@code StpSandboxCheck} sends a real order to STP's
 * demo with the account's own credentials, which confirms them before going live.
 */
public class StpPayoutGateway implements PayoutGateway {

    static final String STP_INSTITUTION = "90646";
    static final String CURRENCY_NOT_SUPPORTED = "CURRENCY_NOT_SUPPORTED";
    private static final String SPEI_CURRENCY = "MXN";

    private static final Logger log = LoggerFactory.getLogger(StpPayoutGateway.class);
    private static final String KEY_PREFIX = "BDW";
    private static final int KEY_RANDOM_LENGTH = 20;
    private static final BigInteger KEY_RANDOM_RANGE = BigInteger.valueOf(36).pow(KEY_RANDOM_LENGTH);
    private static final DateTimeFormatter KEY_DAY = DateTimeFormatter.ofPattern("uuMMdd");
    private static final DateTimeFormatter OPERATION_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CLABE_ACCOUNT = "40";
    private static final String THIRD_PARTY_PAYMENT = "1";
    private static final String SENT_ORDER = "E";
    // Sent the way STP's own clients send them: third party to third party, by web service, normal priority
    private static final String TOPOLOGY = "T";
    private static final String DELIVERY_BY_WEB_SERVICE = "3";
    private static final String NORMAL_PRIORITY = "0";
    // STP refuses a tracking key it already has; the order may then be on a day the lookup doesn't reach
    private static final long KEY_IN_USE = -1;
    // STP accepts "ND" (not available) when the beneficiary's RFC or CURP is not known
    private static final String UNKNOWN_RFC = "ND";
    // The longest text STP clients send, one under SPEI's 40, which some participants count differently
    private static final int NAME_LENGTH = 39;
    private static final int QUERY_FOUND = 0;
    private static final int QUERY_NOT_FOUND = 6;
    // Settled at the receiving bank, then the steps of its electronic receipt (CEP)
    private static final Set<String> PAID = Set.of("LQ", "TLQ", "CXO", "CCE", "CCO");
    // Returned by the receiving bank, or cancelled or rejected (by STP, its adapter or Banxico) before it left: the
    // money is back in the STP account
    private static final Set<String> RETURNED = Set.of("D", "RE", "CN", "TCL", "CL", "CA", "EL", "RL", "RA", "RB");
    // Banxico codes these development banks with 37 instead of 40
    private static final Set<String> DEVELOPMENT_BANKS = Set.of("006", "009", "019", "135", "166", "168");

    private final RestClient ordersClient;
    private final RestClient queryClient;
    private final StpSigner signer;
    private final StpProperties properties;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StpPayoutGateway(RestClient ordersClient, RestClient queryClient, StpSigner signer,
            StpProperties properties, Clock clock) {
        this.ordersClient = ordersClient;
        this.queryClient = queryClient;
        this.signer = signer;
        this.properties = properties;
        this.clock = clock;
    }

    // SPEI moves only pesos
    @Override
    public boolean pays(Currency currency) {
        return SPEI_CURRENCY.equals(currency.getCurrencyCode());
    }

    @Override
    public PayoutResult send(PayoutOrder order) {
        LocalDate requestedOn = LocalDate.ofInstant(order.requestedAt(), properties.timeZone());
        String trackingKey = trackingKey(order.withdrawalId(), requestedOn);
        if (order.destination() == null || !pays(order.amount().currency())) {
            // Never reaches here: withdrawals in other currencies are refused before they are made. Failing gives
            // the money back.
            return PayoutResult.failed(trackingKey, CURRENCY_NOT_SUPPORTED);
        }
        Optional<OrderState> existing = find(trackingKey, requestedOn);
        if (existing.isPresent()) {
            return toResult(trackingKey, existing.get());
        }
        RegisterResult result = register(order, trackingKey).resultado();
        if (result != null && result.id() != null && result.id() > 0) {
            return PayoutResult.pending(trackingKey);
        }
        // A refused order may still be one STP took a moment ago, from an attempt whose answer was lost
        existing = find(trackingKey, requestedOn);
        if (existing.isPresent()) {
            return toResult(trackingKey, existing.get());
        }
        String description = result == null ? null : result.descripcionError();
        if (result == null || result.id() == null || result.id() == 0 || result.id() == KEY_IN_USE) {
            // Not a refusal of this payout: STP had no answer, our request is wrong (an invalid signature, a missing
            // field) or it has the order where the lookup doesn't reach. Giving the money back could pay it twice,
            // so it stays pending, is sent again and the stuck alert fires if it never goes through
            log.error("STP did not take the payout of withdrawal {}: {} {}", order.withdrawalId(),
                    result == null ? null : result.id(), description);
            throw new PayoutProviderUnavailableException();
        }
        String code = Long.toString(-result.id());
        log.warn("STP refused the payout of withdrawal {} ({}): {}", order.withdrawalId(), code, description);
        return PayoutResult.failed(trackingKey, "STP_REJECTED_" + code);
    }

    @Override
    public PayoutResult check(String reference) {
        LocalDate requestedOn = requestedOn(reference);
        return find(reference, requestedOn)
                .map(state -> toResult(reference, state))
                .orElseGet(() -> {
                    // Registered but not readable yet; asked again on the next run, and the stuck alert fires if
                    // it never shows up
                    log.warn("STP has no order with tracking key {} yet", reference);
                    return PayoutResult.pending(reference);
                });
    }

    /**
     * {@code BDW}, the day the withdrawal was asked for ({@code yyMMdd}, which tells {@link #check} where to look)
     * and 20 base-36 characters from its id: 29 characters, which every STP client accepts (SPEI allows 30).
     */
    static String trackingKey(UUID withdrawalId, LocalDate requestedOn) {
        BigInteger id = new BigInteger(1, toBytes(withdrawalId));
        String random = id.mod(KEY_RANDOM_RANGE).toString(36).toUpperCase(Locale.ROOT);
        return KEY_PREFIX + KEY_DAY.format(requestedOn) + "0".repeat(KEY_RANDOM_LENGTH - random.length()) + random;
    }

    /**
     * The 5-digit SPEI code of the bank a CLABE belongs to, from its first three digits: banks are 40 and the
     * code, development banks 37, and other participants (brokers, payment institutions such as STP) 90.
     */
    static String institutionOf(String clabe) {
        String bank = clabe.substring(0, 3);
        if (bank.charAt(0) >= '6') {
            return "90" + bank;
        }
        return (DEVELOPMENT_BANKS.contains(bank) ? "37" : "40") + bank;
    }

    /**
     * Without accents or symbols, which SPEI does not carry, and at most 40 characters.
     */
    static String speiText(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return plain.length() > NAME_LENGTH ? plain.substring(0, NAME_LENGTH).strip() : plain;
    }

    private RegisterResponse register(PayoutOrder order, String trackingKey) {
        BigDecimal amount = order.amount().toDecimal().setScale(2, RoundingMode.UNNECESSARY);
        String counterparty = institutionOf(order.destination().clabe());
        String beneficiary = speiText(order.destination().holderName());
        String payer = speiText(properties.accountHolder());
        String concept = speiText(properties.concept());
        String reference = Long.toString(numericReference(order.withdrawalId()));
        // The order STP signs, empty places included (fields this payout does not use)
        List<String> signed = new ArrayList<>(List.of(counterparty, properties.company(), "", "", trackingKey,
                STP_INSTITUTION, amount.toPlainString(), THIRD_PARTY_PAYMENT, CLABE_ACCOUNT, payer, properties.account(),
                properties.accountHolderRfc(), CLABE_ACCOUNT, beneficiary, order.destination().clabe(), UNKNOWN_RFC,
                "", "", "", "", "", concept, "", "", "", "", "", reference, "", TOPOLOGY, "", DELIVERY_BY_WEB_SERVICE,
                NORMAL_PRIORITY, ""));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claveRastreo", trackingKey);
        body.put("conceptoPago", concept);
        body.put("cuentaOrdenante", properties.account());
        body.put("cuentaBeneficiario", order.destination().clabe());
        body.put("empresa", properties.company());
        body.put("institucionContraparte", Integer.parseInt(counterparty));
        body.put("institucionOperante", Integer.parseInt(STP_INSTITUTION));
        body.put("monto", amount);
        body.put("nombreBeneficiario", beneficiary);
        body.put("nombreOrdenante", payer);
        body.put("referenciaNumerica", Integer.parseInt(reference));
        body.put("rfcCurpBeneficiario", UNKNOWN_RFC);
        body.put("rfcCurpOrdenante", properties.accountHolderRfc());
        body.put("tipoCuentaBeneficiario", Integer.parseInt(CLABE_ACCOUNT));
        body.put("tipoCuentaOrdenante", Integer.parseInt(CLABE_ACCOUNT));
        body.put("tipoPago", Integer.parseInt(THIRD_PARTY_PAYMENT));
        body.put("topologia", TOPOLOGY);
        body.put("medioEntrega", Integer.parseInt(DELIVERY_BY_WEB_SERVICE));
        body.put("prioridad", Integer.parseInt(NORMAL_PRIORITY));
        body.put("firma", signer.sign(signed));
        return call("register the payout " + trackingKey, () -> ordersClient.put()
                .uri("/ordenPago/registra")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class), RegisterResponse.class);
    }

    // The order's day first, where nearly every payout is, then the following ones in case STP dated it later
    private Optional<OrderState> find(String trackingKey, LocalDate requestedOn) {
        LocalDate last = LocalDate.now(clock.withZone(properties.timeZone())).plusDays(1);
        for (int offset = 0; offset <= properties.lookupDays(); offset++) {
            LocalDate day = requestedOn.plusDays(offset);
            if (day.isAfter(last)) {
                break;
            }
            Optional<OrderState> state = query(trackingKey, day);
            if (state.isPresent()) {
                return state;
            }
        }
        return Optional.empty();
    }

    private Optional<OrderState> query(String trackingKey, LocalDate day) {
        String operationDay = OPERATION_DAY.format(day);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("empresa", properties.company());
        body.put("claveRastreo", trackingKey);
        body.put("tipoOrden", SENT_ORDER);
        body.put("fechaOperacion", operationDay);
        body.put("firma", signer.sign(List.of(properties.company(), trackingKey, SENT_ORDER, operationDay)));
        QueryResponse response = call("look up the payout " + trackingKey, () -> queryClient.post()
                .uri("/consultaOrden")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class), QueryResponse.class);
        if (response.estado() == QUERY_FOUND && response.respuesta() != null) {
            return Optional.of(response.respuesta());
        }
        if (response.estado() == QUERY_NOT_FOUND) {
            return Optional.empty();
        }
        log.warn("STP could not look up the payout {}: {} {}", trackingKey, response.estado(), response.mensaje());
        throw new PayoutProviderUnavailableException();
    }

    private static PayoutResult toResult(String trackingKey, OrderState state) {
        String code = state.estado() == null ? "" : state.estado().strip().toUpperCase(Locale.ROOT);
        if (PAID.contains(code)) {
            return PayoutResult.paid(trackingKey);
        }
        if (RETURNED.contains(code)) {
            String cause = state.causaDevolucion() == null || state.causaDevolucion().isBlank()
                    ? code : code + "_" + state.causaDevolucion().strip();
            return PayoutResult.failed(trackingKey, "STP_RETURNED_" + cause);
        }
        return PayoutResult.pending(trackingKey);
    }

    private <T> T call(String action, Supplier<String> request, Class<T> type) {
        String body;
        try {
            body = request.get();
        } catch (RestClientException e) {
            log.warn("Could not reach STP to {}: {}", action, e.getMessage());
            throw new PayoutProviderUnavailableException();
        }
        if (body == null) {
            throw new PayoutProviderUnavailableException();
        }
        try {
            return jsonMapper.readValue(body, type);
        } catch (JacksonException e) {
            log.warn("Unreadable answer from STP to {}", action);
            throw new PayoutProviderUnavailableException();
        }
    }

    // A payout sent by another provider before the switch to STP: kept pending (the stuck alert fires) instead of
    // stopping the payout job's whole batch
    private static LocalDate requestedOn(String trackingKey) {
        try {
            if (trackingKey.startsWith(KEY_PREFIX)) {
                return LocalDate.parse(trackingKey.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + 6),
                        KEY_DAY);
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            // Falls through to the error below
        }
        log.error("Payout reference {} was not made by STP; it needs a person", trackingKey);
        throw new PayoutProviderUnavailableException();
    }

    // STP's referenciaNumerica: up to 7 digits, never zero
    private static long numericReference(UUID withdrawalId) {
        return Math.floorMod(withdrawalId.getMostSignificantBits(), 9_999_999L) + 1;
    }

    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisterResponse(RegisterResult resultado) {
    }

    /**
     * @param id the order's id at STP when registered; zero or negative (the error code) when refused
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisterResult(Long id, String descripcionError) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResponse(int estado, String mensaje, OrderState respuesta) {
    }

    /**
     * @param estado          the order's state code, e.g. LQ (settled) or D (returned)
     * @param causaDevolucion why the receiving bank returned it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderState(String claveRastreo, String estado, String causaDevolucion) {
    }
}
