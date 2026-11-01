package com.irvinsegura.wallet.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The parts of Stripe's API the application calls, served from memory on a local port: Checkout Sessions (create,
 * read, expire) and refunds. Tests decide what the payer does with {@link #pay} and build the webhooks Stripe would
 * send with {@link #event}.
 */
public final class FakeStripe implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final List<String> refundedPaymentIntents = new CopyOnWriteArrayList<>();
    private final AtomicBoolean down = new AtomicBoolean();

    public FakeStripe() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1/", this::handle);
        server.start();
    }

    public String apiUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * Makes every call fail with a 500 until turned off again.
     */
    public void setDown(boolean isDown) {
        down.set(isDown);
    }

    /**
     * The session opened for a payment (a deposit shares its id).
     */
    public String sessionFor(String paymentId) {
        return sessions.values().stream()
                .filter(session -> session.paymentId.equals(paymentId))
                .map(session -> session.id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No checkout session for payment " + paymentId));
    }

    /**
     * The payer completes the checkout with a card.
     */
    public void pay(String sessionId) {
        Session session = sessions.get(sessionId);
        session.status = "complete";
        session.paymentStatus = "paid";
        session.paymentIntent = "pi_" + sessionId;
    }

    /**
     * The checkout reaches its expiry without a payment.
     */
    public void expire(String sessionId) {
        sessions.get(sessionId).status = "expired";
    }

    public String status(String sessionId) {
        return sessions.get(sessionId).status;
    }

    public String paymentIntentOf(String sessionId) {
        return sessions.get(sessionId).paymentIntent;
    }

    public List<String> refundedPaymentIntents() {
        return List.copyOf(refundedPaymentIntents);
    }

    /**
     * The webhook body Stripe sends for an event about this session, e.g. {@code checkout.session.completed}.
     */
    public String event(String type, String sessionId) {
        return """
                {"id": "evt_%s", "object": "event", "type": "%s", "data": {"object": %s}}"""
                .formatted(UUID.randomUUID(), type, sessions.get(sessionId).toJson());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (down.get()) {
                respond(exchange, 500, "{\"error\": {\"message\": \"Stripe is down\"}}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            if (method.equals("POST") && path.equals("/v1/checkout/sessions")) {
                Session session = new Session(form);
                sessions.put(session.id, session);
                respond(exchange, 200, session.toJson());
            } else if (method.equals("GET") && path.startsWith("/v1/checkout/sessions/")) {
                respond(exchange, 200, sessions.get(path.substring("/v1/checkout/sessions/".length())).toJson());
            } else if (method.equals("POST") && path.endsWith("/expire")) {
                Session session = sessions.get(path.split("/")[4]);
                if (!session.status.equals("open")) {
                    respond(exchange, 400, "{\"error\": {\"message\": \"Only open sessions can be expired\"}}");
                    return;
                }
                session.status = "expired";
                respond(exchange, 200, session.toJson());
            } else if (method.equals("POST") && path.equals("/v1/refunds")) {
                refundedPaymentIntents.add(form.get("payment_intent"));
                respond(exchange, 200, "{\"id\": \"re_%s\", \"object\": \"refund\"}".formatted(UUID.randomUUID()));
            } else {
                respond(exchange, 404, "{\"error\": {\"message\": \"Unknown request\"}}");
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        if (body.isEmpty()) {
            return form;
        }
        for (String pair : body.split("&")) {
            String[] keyValue = pair.split("=", 2);
            form.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                    keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "");
        }
        return form;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static final class Session {

        private final String id = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
        private final String paymentId;
        private final long amount;
        private final String currency;
        private final long expiresAt;
        private volatile String status = "open";
        private volatile String paymentStatus = "unpaid";
        private volatile String paymentIntent;

        private Session(Map<String, String> form) {
            this.paymentId = form.get("client_reference_id");
            this.amount = Long.parseLong(form.get("line_items[0][price_data][unit_amount]"));
            this.currency = form.get("line_items[0][price_data][currency]");
            this.expiresAt = Long.parseLong(form.get("expires_at"));
        }

        private String toJson() {
            List<String> fields = new ArrayList<>();
            fields.add("\"id\": \"%s\"".formatted(id));
            fields.add("\"object\": \"checkout.session\"");
            fields.add("\"url\": \"https://checkout.stripe.test/c/pay/%s\"".formatted(id));
            fields.add("\"status\": \"%s\"".formatted(status));
            fields.add("\"payment_status\": \"%s\"".formatted(paymentStatus));
            fields.add("\"payment_intent\": " + (paymentIntent == null ? "null" : "\"" + paymentIntent + "\""));
            fields.add("\"client_reference_id\": \"%s\"".formatted(paymentId));
            fields.add("\"amount_total\": " + amount);
            fields.add("\"currency\": \"%s\"".formatted(currency));
            fields.add("\"expires_at\": " + expiresAt);
            return "{" + String.join(", ", fields) + "}";
        }
    }
}
