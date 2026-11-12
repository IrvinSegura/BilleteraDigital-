package com.irvinsegura.wallet.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two STP services the application calls, served from memory on a local port: registering an order (SpeiWS
 * {@code PUT /ordenPago/registra}) and reading it by tracking key (EFWS {@code POST /consultaOrden}). Every request's
 * signature is checked against the public half of {@link #privateKeyPem()}, rebuilding the signed string from the
 * fields received, so a field signed out of place fails the call the way STP would.
 * <p>
 * Tests decide what happens to an order with {@link #settle}, and make STP refuse one with {@link #rejectNext} or lose
 * its answer with {@link #loseNextAnswer}.
 */
public final class FakeStp implements AutoCloseable {

    /**
     * The order of the signed fields of a new order, as STP's manual gives it.
     */
    private static final List<String> ORDER_FIELDS = List.of("institucionContraparte", "empresa", "fechaOperacion",
            "folioOrigen", "claveRastreo", "institucionOperante", "monto", "tipoPago", "tipoCuentaOrdenante",
            "nombreOrdenante", "cuentaOrdenante", "rfcCurpOrdenante", "tipoCuentaBeneficiario", "nombreBeneficiario",
            "cuentaBeneficiario", "rfcCurpBeneficiario", "emailBeneficiario", "tipoCuentaBeneficiario2",
            "nombreBeneficiario2", "cuentaBeneficiario2", "rfcCurpBeneficiario2", "conceptoPago", "conceptoPago2",
            "claveCatUsuario1", "claveCatUsuario2", "clavePago", "referenciaCobranza", "referenciaNumerica",
            "tipoOperacion", "topologia", "usuario", "medioEntrega", "prioridad", "iva");
    private static final List<String> QUERY_FIELDS = List.of("empresa", "claveRastreo", "tipoOrden",
            "fechaOperacion");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final HttpServer server;
    private final KeyPair keys;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1000);
    private final AtomicInteger registrations = new AtomicInteger();
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicBoolean loseNextAnswer = new AtomicBoolean();
    private final AtomicReference<Integer> rejectNext = new AtomicReference<>();
    private final AtomicReference<LocalDate> operationDay = new AtomicReference<>();
    private final AtomicReference<JsonNode> lastOrder = new AtomicReference<>();

    public FakeStp() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public String privateKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keys.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    /**
     * Makes every call fail with a 500 until turned off again.
     */
    public void setDown(boolean isDown) {
        down.set(isDown);
    }

    /**
     * Registers the next order but answers with a 500, like an answer lost on its way back.
     */
    public void loseNextAnswer() {
        loseNextAnswer.set(true);
    }

    /**
     * Refuses the next order with this STP error code (negative, as STP sends it).
     */
    public void rejectNext(int code) {
        rejectNext.set(code);
    }

    /**
     * The operation day STP gives new orders; today in Mexico City when not set.
     */
    public void setOperationDay(LocalDate day) {
        operationDay.set(day);
    }

    public int registrations() {
        return registrations.get();
    }

    /**
     * The fields of the order registered last; fails if there is none.
     */
    public JsonNode lastOrder() {
        JsonNode order = lastOrder.get();
        if (order == null) {
            throw new AssertionError("STP has registered no order");
        }
        return order;
    }

    /**
     * The order moves to this STP state, e.g. {@code LQ} (settled) or {@code D} (returned, with a cause).
     */
    public void settle(String trackingKey, String state, String returnCause) {
        Order order = orders.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(trackingKey + "|"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No STP order with tracking key " + trackingKey));
        order.state = state;
        order.returnCause = returnCause;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (down.get()) {
                respond(exchange, 500, "{}");
                return;
            }
            JsonNode body = jsonMapper.readTree(exchange.getRequestBody().readAllBytes());
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("PUT".equals(method) && path.endsWith("/ordenPago/registra")) {
                register(exchange, body);
            } else if ("POST".equals(method) && path.endsWith("/consultaOrden")) {
                query(exchange, body);
            } else {
                respond(exchange, 404, "{}");
            }
        }
    }

    private void register(HttpExchange exchange, JsonNode body) throws IOException {
        if (!signedCorrectly(body, ORDER_FIELDS)) {
            // STP answers a request it can't validate with id 0
            respond(exchange, 200, "{\"resultado\":{\"id\":0,\"descripcionError\":\"Error validando la firma\"}}");
            return;
        }
        Integer rejection = rejectNext.getAndSet(null);
        if (rejection != null) {
            respond(exchange, 200, "{\"resultado\":{\"id\":%d,\"descripcionError\":\"Orden rechazada\"}}"
                    .formatted(rejection));
            return;
        }
        String trackingKey = body.path("claveRastreo").asString();
        LocalDate day = operationDay.get() == null ? LocalDate.now(ZoneId.of("America/Mexico_City"))
                : operationDay.get();
        String key = trackingKey + "|" + DAY.format(day);
        if (orders.containsKey(key)) {
            respond(exchange, 200, ("{\"resultado\":{\"id\":-1,\"descripcionError\":\"La clave de rastreo %s ya fue "
                    + "utilizada\"}}").formatted(trackingKey));
            return;
        }
        long id = nextId.incrementAndGet();
        orders.put(key, new Order());
        lastOrder.set(body);
        registrations.incrementAndGet();
        if (loseNextAnswer.getAndSet(false)) {
            respond(exchange, 500, "{}");
            return;
        }
        respond(exchange, 200, "{\"resultado\":{\"id\":%d}}".formatted(id));
    }

    private void query(HttpExchange exchange, JsonNode body) throws IOException {
        if (!signedCorrectly(body, QUERY_FIELDS)) {
            respond(exchange, 200, "{\"estado\":1,\"mensaje\":\"Firma invalida\"}");
            return;
        }
        Order order = orders.get(body.path("claveRastreo").asString() + "|"
                + body.path("fechaOperacion").asString());
        if (order == null) {
            respond(exchange, 200, "{\"estado\":6,\"mensaje\":\"No se encontraron datos relacionados\"}");
            return;
        }
        String cause = order.returnCause == null ? "null" : "\"" + order.returnCause + "\"";
        respond(exchange, 200, ("{\"estado\":0,\"mensaje\":\"Datos consultados correctamente\",\"respuesta\":"
                + "{\"claveRastreo\":\"%s\",\"estado\":\"%s\",\"causaDevolucion\":%s}}")
                .formatted(body.path("claveRastreo").asString(), order.state, cause));
    }

    private boolean signedCorrectly(JsonNode body, List<String> fields) {
        StringBuilder original = new StringBuilder("||");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                original.append('|');
            }
            JsonNode value = body.path(fields.get(i));
            if (!value.isMissingNode() && !value.isNull()) {
                original.append(value.isNumber() && "monto".equals(fields.get(i))
                        ? value.decimalValue().setScale(2).toPlainString() : value.asString());
            }
        }
        original.append("||");
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(keys.getPublic());
            signature.update(original.toString().getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(body.path("firma").asString()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
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

    private static final class Order {

        // Authorized: on its way to the receiving bank
        private volatile String state = "A";
        private volatile String returnCause;
    }
}
