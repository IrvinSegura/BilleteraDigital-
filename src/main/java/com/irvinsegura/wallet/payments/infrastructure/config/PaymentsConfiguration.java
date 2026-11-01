package com.irvinsegura.wallet.payments.infrastructure.config;

import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.infrastructure.simulated.SimulatedPaymentGateway;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripePaymentGateway;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeSignatureVerifier;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentsProperties.class)
class PaymentsConfiguration {

    private static final String PROVIDER = "wallet.payments.provider";
    private static final Duration STRIPE_MIN_CHECKOUT_TTL = Duration.ofMinutes(30);
    private static final Duration STRIPE_MAX_CHECKOUT_TTL = Duration.ofHours(24);

    @Bean
    @ConditionalOnProperty(name = PROVIDER, havingValue = "simulated")
    PaymentGateway simulatedPaymentGateway() {
        return new SimulatedPaymentGateway();
    }

    // Startup fails on missing keys instead of every deposit failing later
    @Bean
    @ConditionalOnProperty(name = PROVIDER, havingValue = "stripe")
    PaymentGateway stripePaymentGateway(PaymentsProperties properties, Clock clock,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        PaymentsProperties.Stripe stripe = properties.stripe();
        if (isBlank(stripe.apiKey()) || isBlank(stripe.webhookSecret())) {
            throw new IllegalStateException("The payment provider is stripe but wallet.payments.stripe.api-key "
                    + "(STRIPE_API_KEY) or wallet.payments.stripe.webhook-secret (STRIPE_WEBHOOK_SECRET) is not set");
        }
        if (properties.checkoutTtl().compareTo(STRIPE_MIN_CHECKOUT_TTL) < 0
                || properties.checkoutTtl().compareTo(STRIPE_MAX_CHECKOUT_TTL) > 0) {
            throw new IllegalStateException("wallet.payments.checkout-ttl must be between 30 minutes and 24 hours");
        }
        // Without timeouts a hung provider would hold the request thread forever
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(stripe.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(stripe.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(stripe.apiUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stripe.apiKey())
                // Calls show up as spans in traces, next to the request that made them
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP))
                .build();
        return new StripePaymentGateway(restClient,
                new StripeSignatureVerifier(stripe.webhookSecret(), stripe.signatureTolerance(), clock),
                properties.checkoutTtl(), properties.returnUrl(), clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
