package com.irvinsegura.wallet.transfers.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxDeposit        largest single deposit, by ISO currency code, e.g. {@code MXN: 100000.00}
 * @param stepUpExemptBelow transfers under this amount to someone paid before skip the step-up check, by ISO currency
 *                          code; currencies left out always ask for it
 */
@Validated
@ConfigurationProperties("wallet.transfers")
public record TransfersProperties(@NotNull Map<String, BigDecimal> maxDeposit,
        @NotNull Map<String, BigDecimal> stepUpExemptBelow) {
}
