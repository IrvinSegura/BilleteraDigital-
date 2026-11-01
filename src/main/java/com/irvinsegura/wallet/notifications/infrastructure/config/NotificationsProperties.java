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
     * @param from   sender address, optionally with a display name, e.g. {@code Digital Wallet <no-reply@example.com>}
     * @param appUrl base address of the client app that opens the links in account emails
     */
    public record Email(@NotNull Delivery delivery, @NotBlank String from, @NotBlank String appUrl) {
    }

    public enum Delivery {
        SMTP,
        LOG
    }
}
