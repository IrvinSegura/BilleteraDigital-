package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Picks the payout provider. Startup fails without one, so a production deployment never pays withdrawals with the
 * simulated provider by accident, and fails too when STP is chosen without everything it needs.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StpProperties.class, OpenpayProperties.class})
class PayoutsConfiguration {

    @Bean
    PayoutGateway payoutGateway(@Value("${wallet.transfers.payouts.provider:}") String provider,
            StpProperties stp, OpenpayProperties openpay, Clock clock,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        ObservationRegistry observations = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
        return switch (provider) {
            case "simulated" -> new SimulatedPayoutGateway();
            case "stp" -> stpPayoutGateway(stp, clock, observations);
            case "openpay" -> openpayPayoutGateway(openpay, observations);
            default -> throw new IllegalStateException(("wallet.transfers.payouts.provider (PAYOUT_PROVIDER) is "
                    + "'%s'; use 'stp' or 'openpay' to pay MXN withdrawals to a CLABE, or 'simulated', which sends no "
                    + "real money, only for demo deployments").formatted(provider));
        };
    }

    private static PayoutGateway openpayPayoutGateway(OpenpayProperties openpay,
            ObservationRegistry observationRegistry) {
        Map<String, String> required = new LinkedHashMap<>();
        required.put("url (OPENPAY_URL)", openpay.url());
        required.put("merchant-id (OPENPAY_MERCHANT_ID)", openpay.merchantId());
        required.put("private-key (OPENPAY_PRIVATE_KEY)", openpay.privateKey());
        List<String> missing = missing(required);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The payout provider is openpay but wallet.transfers.payouts.openpay is "
                    + "missing " + String.join(", ", missing));
        }
        if (!openpay.privateKey().startsWith("sk_")) {
            throw new IllegalStateException("wallet.transfers.payouts.openpay.private-key must be the private key "
                    + "(sk_...), not the public one");
        }
        RestClient client = OpenpayPayoutGateway.authenticated(builder(openpay.connectTimeout(),
                openpay.readTimeout(), observationRegistry), openpay).build();
        return new OpenpayPayoutGateway(client, openpay);
    }

    private static List<String> missing(Map<String, String> required) {
        return required.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static PayoutGateway stpPayoutGateway(StpProperties stp, Clock clock,
            ObservationRegistry observationRegistry) {
        Map<String, String> required = new LinkedHashMap<>();
        required.put("orders-url (STP_ORDERS_URL)", stp.ordersUrl());
        required.put("query-url (STP_QUERY_URL)", stp.queryUrl());
        required.put("company (STP_COMPANY)", stp.company());
        required.put("account (STP_ACCOUNT)", stp.account());
        required.put("account-holder (STP_ACCOUNT_HOLDER)", stp.accountHolder());
        required.put("account-holder-rfc (STP_ACCOUNT_HOLDER_RFC)", stp.accountHolderRfc());
        required.put("private-key (STP_PRIVATE_KEY)", stp.privateKey());
        List<String> missing = missing(required);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The payout provider is stp but wallet.transfers.payouts.stp is missing "
                    + String.join(", ", missing));
        }
        if (!stp.account().matches("\\d{18}")) {
            throw new IllegalStateException("wallet.transfers.payouts.stp.account must be the 18-digit CLABE of the "
                    + "platform's STP account");
        }
        return new StpPayoutGateway(restClient(stp.ordersUrl(), stp, observationRegistry),
                restClient(stp.queryUrl(), stp, observationRegistry), StpSigner.fromPem(stp.privateKey()), stp,
                clock);
    }

    // Without timeouts a hung provider would hold the request thread forever
    private static RestClient restClient(String baseUrl, StpProperties stp, ObservationRegistry observationRegistry) {
        return builder(stp.connectTimeout(), stp.readTimeout(), observationRegistry).baseUrl(baseUrl).build();
    }

    private static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout,
            ObservationRegistry observationRegistry) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                // Calls show up as spans in traces, next to the request that made them
                .observationRegistry(observationRegistry);
    }
}
