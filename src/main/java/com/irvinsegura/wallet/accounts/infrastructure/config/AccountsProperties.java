package com.irvinsegura.wallet.accounts.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param dailyOutgoingLimit maximum per wallet and local day, by ISO currency code, e.g. {@code MXN: 50000.00}
 * @param limitZone          time zone that defines when a day starts
 */
@Validated
@ConfigurationProperties("wallet.accounts")
public record AccountsProperties(@NotNull Map<String, BigDecimal> dailyOutgoingLimit, @NotNull ZoneId limitZone) {
}
