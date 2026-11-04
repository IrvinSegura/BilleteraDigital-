package com.irvinsegura.wallet.shared.web;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Which web apps on other origins may call the API from a browser ({@code wallet.web.cors.allowed-origins}). Empty
 * by default: a front served from the API's own origin, or through a dev server proxy, needs none.
 * <p>
 * Credentials are allowed because browser sessions keep their refresh token in a cookie. That is safe only with an
 * exact list of origins, never a wildcard, so none is accepted.
 */
@Configuration(proxyBeanMethods = false)
class CorsSettings {

    // Picked up by Spring Security's cors() by this name
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${wallet.web.cors.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        if (origins.isEmpty()) {
            return source;
        }
        if (origins.contains("*")) {
            throw new IllegalStateException("wallet.web.cors.allowed-origins must list exact origins, not *");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id",
                "X-Session-Mode"));
        // Headers the API answers with that a web app needs to read
        cors.setExposedHeaders(List.of("Location", "Retry-After", "Idempotent-Replayed", "X-Correlation-Id",
                "X-RateLimit-Remaining"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
