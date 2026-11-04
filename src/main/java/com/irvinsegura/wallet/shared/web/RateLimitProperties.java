package com.irvinsegura.wallet.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rules are checked in order and the first one matching a request applies, so specific rules go before broad ones.
 */
@Validated
@ConfigurationProperties("wallet.rate-limit")
public record RateLimitProperties(boolean enabled, List<@Valid Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * Allows {@code capacity} requests per {@code period} for each client, refilled gradually.
     *
     * @param methods HTTP methods the rule applies to; empty means all
     * @param paths   path patterns, e.g. {@code /api/v1/auth/**}
     */
    public record Rule(
            @NotBlank String name,
            List<String> methods,
            @NotEmpty List<String> paths,
            @NotNull ClientKey key,
            @Min(1) long capacity,
            @NotNull Duration period) {

        public Rule {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }
    }

    public enum ClientKey {
        // Client address; the only option before login
        IP,
        // Authenticated user id, falling back to the address for anonymous requests
        USER
    }
}
