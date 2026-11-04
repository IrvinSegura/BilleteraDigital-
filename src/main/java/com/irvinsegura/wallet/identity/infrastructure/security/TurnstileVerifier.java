package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.port.HumanVerifier;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare Turnstile's siteverify API. Unlike the breached password lookup it fails closed: when Cloudflare cannot
 * be reached nobody passes, since letting everyone through would switch the protection off.
 */
class TurnstileVerifier implements HumanVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    // Turnstile tokens are at most 2048 characters
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*true");

    private final HttpClient httpClient;
    private final URI verifyUrl;
    private final String secretKey;
    private final Duration timeout;

    TurnstileVerifier(URI verifyUrl, String secretKey, Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.verifyUrl = verifyUrl;
        this.secretKey = secretKey;
        this.timeout = timeout;
    }

    @Override
    public boolean isHuman(String token, String remoteIp) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("secret", secretKey);
        form.put("response", token);
        if (remoteIp != null) {
            form.put("remoteip", remoteIp);
        }
        HttpRequest request = HttpRequest.newBuilder(verifyUrl)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Human check refused: Turnstile answered {}", response.statusCode());
                return false;
            }
            return SUCCESS.matcher(response.body()).find();
        } catch (IOException e) {
            log.warn("Human check refused: Turnstile unreachable ({})", e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
