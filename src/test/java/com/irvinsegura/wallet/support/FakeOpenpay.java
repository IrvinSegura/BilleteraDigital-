package com.irvinsegura.wallet.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Openpay's payout API as the application calls it, served from memory on a local port: {@code POST
 * /v1/{merchant}/payouts}, {@code GET /v1/{merchant}/payouts/{id}} and the list. Every call must carry the merchant in
 * its path and the private key as its HTTP Basic user, and an {@code order_id} is taken once ({@code 409}, error 1006).
 * <p>
 * Tests decide what happens to a payout with {@link #settle}, make Openpay refuse one with {@link #rejectNext}, run out
 * of balance with {@link #setOutOfFunds} or lose its answer with {@link #loseNextAnswer}.
 */
public final class FakeOpenpay implements AutoCloseable {

    public static final String MERCHANT_ID = "mzdtln0bmtms6o3kck8f";
    public static final String PRIVATE_KEY = "sk_e568c42a6c384b7ab02cd47d2e407cab";

    private final HttpServer server;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Map<String, ObjectNode> payouts = new ConcurrentHashMap<>();
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger nextId = new AtomicInteger(1000);
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicBoolean outOfFunds = new AtomicBoolean();
    private final AtomicBoolean loseNextAnswer = new AtomicBoolean();
    private final AtomicReference<int[]> rejectNext = new AtomicReference<>();
    private final AtomicReference<JsonNode> lastPayout = new AtomicReference<>();

    public FakeOpenpay() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void setDown(boolean isDown) {
        down.set(isDown);
    }

    /**
     * Refuses every payout with error 4001 until turned off: the platform's balance at Openpay is short.
     */
    public void setOutOfFunds(boolean isOut) {
        outOfFunds.set(isOut);
    }

    /**
     * Makes the next payout but answers with a 500, like an answer lost on its way back.
     */
    public void loseNextAnswer() {
        loseNextAnswer.set(true);
    }

    /**
     * Refuses the next payout with this HTTP status and Openpay error code, e.g. 400 and 1001 for an invalid CLABE.
     */
    public void rejectNext(int httpStatus, int errorCode) {
        rejectNext.set(new int[] {httpStatus, errorCode});
    }

    public int created() {
        return created.get();
    }

    /**
     * The request of the payout made last; fails if there is none.
     */
    public JsonNode lastPayout() {
        JsonNode payout = lastPayout.get();
        if (payout == null) {
            throw new AssertionError("Openpay has made no payout");
        }
        return payout;
    }

    /**
     * The id Openpay gave the payout of this order.
     */
    public String idOf(String orderId) {
        return payouts.values().stream()
                .filter(payout -> orderId.equals(payout.path("order_id").asString()))
                .map(payout -> payout.path("id").asString())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Openpay payout for order " + orderId));
    }

    /**
     * The payout moves to this status: {@code completed} or {@code failed}.
     */
    public void settle(String payoutId, String status) {
        ObjectNode payout = payouts.get(payoutId);
        if (payout == null) {
            throw new AssertionError("No Openpay payout " + payoutId);
        }
        payout.put("status", status);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (down.get()) {
                respond(exchange, 500, error(500, 1000, "Internal server error"));
                return;
            }
            String expected = "Basic " + Base64.getEncoder()
                    .encodeToString((PRIVATE_KEY + ":").getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, error(401, 1002, "The api key or merchant id are invalid"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String prefix = "/v1/" + MERCHANT_ID + "/payouts";
            if (!path.startsWith(prefix)) {
                respond(exchange, 404, error(404, 1005, "The requested resource doesn't exist"));
                return;
            }
            String method = exchange.getRequestMethod();
            if ("POST".equals(method) && path.equals(prefix)) {
                create(exchange, jsonMapper.readTree(exchange.getRequestBody().readAllBytes()));
            } else if ("GET".equals(method) && path.equals(prefix)) {
                list(exchange);
            } else if ("GET".equals(method)) {
                ObjectNode payout = payouts.get(path.substring(prefix.length() + 1));
                if (payout == null) {
                    respond(exchange, 404, error(404, 1005, "The requested resource doesn't exist"));
                } else {
                    respond(exchange, 200, payout.toString());
                }
            } else {
                respond(exchange, 405, "{}");
            }
        }
    }

    private void create(HttpExchange exchange, JsonNode body) throws IOException {
        if (!"bank_account".equals(body.path("method").asString())
                || body.path("bank_account").path("clabe").asString().length() != 18
                || body.path("bank_account").path("holder_name").asString().isBlank()
                || !body.path("amount").isNumber()) {
            respond(exchange, 400, error(400, 1001, "The request is invalid"));
            return;
        }
        int[] rejection = rejectNext.getAndSet(null);
        if (rejection != null) {
            respond(exchange, rejection[0], error(rejection[0], rejection[1], "Rejected"));
            return;
        }
        if (outOfFunds.get()) {
            respond(exchange, 412, error(412, 4001, "There are not enough funds in the Openpay account"));
            return;
        }
        String orderId = body.path("order_id").asString();
        if (payouts.values().stream().anyMatch(payout -> orderId.equals(payout.path("order_id").asString()))) {
            respond(exchange, 409, error(409, 1006, "The order_id has already been processed"));
            return;
        }
        ObjectNode payout = jsonMapper.createObjectNode();
        payout.put("id", "tr" + nextId.incrementAndGet() + "payout");
        payout.put("transaction_type", "payout");
        payout.put("method", "bank");
        payout.put("status", "in_progress");
        payout.put("order_id", orderId);
        payout.put("currency", "MXN");
        payout.set("amount", body.path("amount"));
        payouts.put(payout.path("id").asString(), payout);
        created.incrementAndGet();
        lastPayout.set(body);
        if (loseNextAnswer.getAndSet(false)) {
            respond(exchange, 500, error(500, 1000, "Internal server error"));
            return;
        }
        respond(exchange, 200, payout.toString());
    }

    private void list(HttpExchange exchange) throws IOException {
        ArrayNode all = jsonMapper.createArrayNode();
        payouts.values().forEach(all::add);
        respond(exchange, 200, all.toString());
    }

    private String error(int httpStatus, int code, String description) {
        ObjectNode error = jsonMapper.createObjectNode();
        error.put("category", httpStatus >= 500 ? "internal" : "request");
        error.put("description", description);
        error.put("http_code", httpStatus);
        error.put("error_code", code);
        error.put("request_id", "req" + nextId.incrementAndGet());
        return error.toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
