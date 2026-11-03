package com.irvinsegura.wallet.transfers.infrastructure.config;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.DepositPolicy;
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
        // Money.of rejects maximums with more decimals than the currency allows, failing fast at startup
        Map<Currency, Money> maximums = properties.maxDeposit().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Currency.getInstance(entry.getKey()),
                        entry -> Money.of(entry.getValue(), entry.getKey())));
        return new DepositPolicy(maximums);
    }
}
