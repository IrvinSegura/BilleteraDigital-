package com.irvinsegura.wallet.payments.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param checkoutTtl how long the payer has to complete a checkout; Stripe accepts 30 minutes to 24 hours
 * @param returnUrl   client app page the provider sends the payer back to, with {@code payment} and {@code result}
 *                    query parameters
 */
@Validated
@ConfigurationProperties("wallet.payments")
public record PaymentsProperties(@NotNull Provider provider, @NotNull Duration checkoutTtl,
        @NotBlank String returnUrl, @NotNull @Valid Stripe stripe) {

    /**
     * Keys are required only when the provider is Stripe; startup fails without them.
     *
     * @param apiKey             secret key, {@code sk_test_...} or {@code sk_live_...}
     * @param webhookSecret      signing secret of the webhook endpoint, {@code whsec_...}
     * @param signatureTolerance webhooks signed longer ago than this are rejected, so a captured one cannot be
     *                           replayed later
     */
    public record Stripe(String apiKey, String webhookSecret, @NotBlank String apiUrl,
            @NotNull Duration signatureTolerance, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    }

    public enum Provider {
        SIMULATED,
        STRIPE
    }
}
