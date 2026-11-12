package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Openpay (BBVA), which pays MXN withdrawals to a CLABE. Its sandbox is free to sign up for, without a company, at
 * {@code sandbox-dashboard.openpay.mx}; the merchant id and private key are on its dashboard.
 *
 * @param url        base URL of the API: the sandbox's by default, {@code https://api.openpay.mx} in production
 * @param merchantId the merchant's id, part of every path
 * @param privateKey the private API key ({@code sk_...}); it authenticates every call, so it never reaches the browser
 * @param concept    description the payout carries
 * @param timeZone   where Openpay's days begin, to look up a payout by the day it was made
 */
@ConfigurationProperties("wallet.transfers.payouts.openpay")
public record OpenpayProperties(
        @DefaultValue("https://sandbox-api.openpay.mx") String url,
        String merchantId,
        String privateKey,
        @DefaultValue("Retiro de billetera") String concept,
        @DefaultValue("America/Mexico_City") ZoneId timeZone,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout) {
}
