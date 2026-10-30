package com.irvinsegura.wallet.accounts.infrastructure.config;

import com.irvinsegura.wallet.accounts.domain.model.DailyLimitPolicy;
import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountsProperties.class)
class AccountsConfiguration {

    @Bean
    DailyLimitPolicy dailyLimitPolicy(AccountsProperties properties) {
        // Money.of rejects limits with more decimals than the currency allows, failing fast at startup
        Map<Currency, Money> limits = properties.dailyOutgoingLimit().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Currency.getInstance(entry.getKey()),
                        entry -> Money.of(entry.getValue(), entry.getKey())));
        return new DailyLimitPolicy(limits, properties.limitZone());
    }
}
