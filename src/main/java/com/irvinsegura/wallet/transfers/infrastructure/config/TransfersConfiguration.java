package com.irvinsegura.wallet.transfers.infrastructure.config;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentPolicy;
import com.irvinsegura.wallet.transfers.domain.model.DepositPolicy;
import com.irvinsegura.wallet.transfers.domain.model.StepUpPolicy;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransfersProperties.class)
class TransfersConfiguration {

    @Bean
    DepositPolicy depositPolicy(TransfersProperties properties) {
        return new DepositPolicy(byCurrency(properties.maxDeposit()));
    }

    @Bean
    StepUpPolicy stepUpPolicy(TransfersProperties properties) {
        return new StepUpPolicy(byCurrency(properties.stepUpExemptBelow()));
    }

    @Bean
    AdjustmentPolicy adjustmentPolicy(TransfersProperties properties) {
        return new AdjustmentPolicy(byCurrency(properties.adjustmentSingleApprovalUpTo()));
    }

    // Money.of rejects amounts with more decimals than the currency allows, failing fast at startup
    private static Map<Currency, Money> byCurrency(Map<String, BigDecimal> amounts) {
        return amounts.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Currency.getInstance(entry.getKey()),
                        entry -> Money.of(entry.getValue(), entry.getKey())));
    }
}
