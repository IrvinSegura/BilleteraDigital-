package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.model.LockoutPolicy;
import com.irvinsegura.wallet.identity.domain.model.OneTimeTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.RefreshTokenPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

    @Bean
    LockoutPolicy lockoutPolicy(IdentityProperties properties) {
        return new LockoutPolicy(properties.lockout().maxFailedAttempts(), properties.lockout().duration());
    }

    @Bean
    RefreshTokenPolicy refreshTokenPolicy(IdentityProperties properties) {
        return new RefreshTokenPolicy(properties.refreshTokenTtl());
    }

    @Bean
    OneTimeTokenPolicy oneTimeTokenPolicy(IdentityProperties properties) {
        return new OneTimeTokenPolicy(properties.emailVerificationTtl(), properties.passwordResetTtl());
    }
}
