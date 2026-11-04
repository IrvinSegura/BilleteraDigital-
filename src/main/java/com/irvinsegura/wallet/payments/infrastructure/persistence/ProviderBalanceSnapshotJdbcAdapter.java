package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.model.ProviderBalanceSnapshot;
import com.irvinsegura.wallet.payments.domain.port.ProviderBalanceSnapshotRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: replacing a row in one statement needs ON CONFLICT.
 */
@Repository
class ProviderBalanceSnapshotJdbcAdapter implements ProviderBalanceSnapshotRepository {

    private final JdbcClient jdbcClient;

    ProviderBalanceSnapshotJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(ProviderBalanceSnapshot snapshot) {
        jdbcClient.sql("""
                        INSERT INTO provider_balance_snapshots (provider, currency, provider_balance_minor,
                            recorded_balance_minor, compared_at)
                        VALUES (:provider, :currency, :providerBalance, :recordedBalance, :comparedAt)
                        ON CONFLICT (provider, currency) DO UPDATE
                        SET provider_balance_minor = EXCLUDED.provider_balance_minor,
                            recorded_balance_minor = EXCLUDED.recorded_balance_minor,
                            compared_at = EXCLUDED.compared_at""")
                .param("provider", snapshot.provider())
                .param("currency", snapshot.currency())
                .param("providerBalance", snapshot.providerBalanceMinor())
                .param("recordedBalance", snapshot.recordedBalanceMinor())
                .param("comparedAt", OffsetDateTime.ofInstant(snapshot.comparedAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<ProviderBalanceSnapshot> findByProvider(String provider) {
        return jdbcClient.sql("""
                        SELECT provider, currency, provider_balance_minor, recorded_balance_minor, compared_at
                        FROM provider_balance_snapshots WHERE provider = :provider ORDER BY currency""")
                .param("provider", provider)
                .query((rs, rowNum) -> new ProviderBalanceSnapshot(rs.getString("provider"), rs.getString("currency"),
                        rs.getLong("provider_balance_minor"), rs.getLong("recorded_balance_minor"),
                        rs.getObject("compared_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
