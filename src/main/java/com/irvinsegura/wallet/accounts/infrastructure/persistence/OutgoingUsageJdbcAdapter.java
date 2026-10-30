package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.port.OutgoingUsageRepository;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: a counter needs an upsert, which JPA cannot express. It runs on the same connection and transaction
 * as the JPA work.
 */
@Repository
class OutgoingUsageJdbcAdapter implements OutgoingUsageRepository {

    private final JdbcClient jdbcClient;

    OutgoingUsageJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Money usedOn(UUID walletId, LocalDate day, Currency currency) {
        long minorUnits = jdbcClient.sql("""
                        SELECT amount_minor FROM wallet_daily_outgoing WHERE wallet_id = :walletId AND day = :day""")
                .param("walletId", walletId)
                .param("day", day)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return new Money(minorUnits, currency);
    }

    @Override
    public void add(UUID walletId, LocalDate day, Money amount) {
        jdbcClient.sql("""
                        INSERT INTO wallet_daily_outgoing (wallet_id, day, amount_minor)
                        VALUES (:walletId, :day, :amount)
                        ON CONFLICT (wallet_id, day)
                        DO UPDATE SET amount_minor = wallet_daily_outgoing.amount_minor + EXCLUDED.amount_minor""")
                .param("walletId", walletId)
                .param("day", day)
                .param("amount", amount.minorUnits())
                .update();
    }
}
