package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * STP, the SPEI participant that pays MXN withdrawals. Only read when it is the payout provider; startup then fails
 * if anything it needs is missing.
 *
 * @param ordersUrl       base URL of STP's order service (SpeiWS), where payouts are registered
 * @param queryUrl        base URL of STP's query service (EFWS), where their state is read
 * @param company         the platform's company name at STP ({@code empresa})
 * @param account         the platform's CLABE at STP, which payouts are paid from ({@code cuentaOrdenante})
 * @param accountHolder   the name that account is under, carried to the receiving bank
 * @param accountHolderRfc the RFC of that holder
 * @param privateKey      the PEM private key whose certificate STP holds, unencrypted PKCS#8
 *                        ({@code -----BEGIN PRIVATE KEY-----}); every request is signed with it
 * @param concept         payment concept the receiving bank shows; sent with up to 39 characters
 * @param timeZone        where STP's operation days begin and end
 * @param lookupDays      how many operation days after the withdrawal's own a payout is looked for
 */
@ConfigurationProperties("wallet.transfers.payouts.stp")
public record StpProperties(
        @DefaultValue("https://demo.stpmex.com:7024/speiws/rest") String ordersUrl,
        @DefaultValue("https://efws-dev.stpmex.com/efws/API") String queryUrl,
        String company,
        String account,
        String accountHolder,
        String accountHolderRfc,
        String privateKey,
        @DefaultValue("Retiro de billetera") String concept,
        @DefaultValue("America/Mexico_City") ZoneId timeZone,
        @DefaultValue("4") int lookupDays,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout) {
}
