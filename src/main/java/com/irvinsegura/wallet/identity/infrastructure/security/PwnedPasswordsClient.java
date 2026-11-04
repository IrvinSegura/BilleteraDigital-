package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.port.BreachedPasswords;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Have I Been Pwned's Pwned Passwords range API. Only the first 5 characters of the password's SHA-1 leave the
 * server (k-anonymity), and padding hides from an observer which suffixes came back.
 */
class PwnedPasswordsClient implements BreachedPasswords {

    private static final Logger log = LoggerFactory.getLogger(PwnedPasswordsClient.class);
    private static final int PREFIX_LENGTH = 5;

    private final HttpClient httpClient;
    private final URI baseUrl;
    private final Duration timeout;

    PwnedPasswordsClient(URI baseUrl, Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    @Override
    public boolean isBreached(String rawPassword) {
        String hash = sha1Hex(rawPassword);
        String prefix = hash.substring(0, PREFIX_LENGTH);
        String suffix = hash.substring(PREFIX_LENGTH);
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("range/" + prefix))
                .timeout(timeout)
                .header("Add-Padding", "true")
                .header("User-Agent", "digital-wallet")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Breached password check skipped: Pwned Passwords answered {}", response.statusCode());
                return false;
            }
            return response.body().lines().anyMatch(line -> isListed(line, suffix));
        } catch (IOException e) {
            log.warn("Breached password check skipped: {}", e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // Lines are SUFFIX:COUNT; padding lines have a count of 0
    private static boolean isListed(String line, String suffix) {
        int colon = line.indexOf(':');
        if (colon != suffix.length() || !line.regionMatches(true, 0, suffix, 0, colon)) {
            return false;
        }
        String count = line.substring(colon + 1).strip();
        return !count.isEmpty() && !count.chars().allMatch(c -> c == '0');
    }

    private static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by every Java runtime", e);
        }
    }
}
