package com.irvinsegura.wallet.notifications.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("wallet.notifications")
public record NotificationsProperties(@NotNull @Valid Email email) {

    /**
     * @param from sender address, optionally with a display name, e.g. {@code Digital Wallet <no-reply@example.com>}
     */
    public record Email(@NotNull Delivery delivery, @NotBlank String from) {
    }

    public enum Delivery {
        SMTP,
        LOG
    }
}
