package com.irvinsegura.wallet.notifications.infrastructure.persistence;

import com.irvinsegura.wallet.notifications.domain.model.EmailPreferences;
import com.irvinsegura.wallet.notifications.domain.port.EmailPreferencesRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EmailPreferencesJdbcAdapter implements EmailPreferencesRepository {

    private final JdbcClient jdbcClient;

    EmailPreferencesJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<EmailPreferences> find(UUID userId) {
        return jdbcClient.sql("""
                        SELECT email_money_in, email_money_out FROM notification_preferences WHERE user_id = :userId""")
                .param("userId", userId)
                .query((rs, row) -> new EmailPreferences(rs.getBoolean("email_money_in"),
                        rs.getBoolean("email_money_out")))
                .optional();
    }

    @Override
    public void save(UUID userId, EmailPreferences preferences, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO notification_preferences (user_id, email_money_in, email_money_out, updated_at)
                        VALUES (:userId, :moneyIn, :moneyOut, :now)
                        ON CONFLICT (user_id) DO UPDATE SET email_money_in = EXCLUDED.email_money_in,
                            email_money_out = EXCLUDED.email_money_out, updated_at = EXCLUDED.updated_at""")
                .param("userId", userId)
                .param("moneyIn", preferences.moneyIn())
                .param("moneyOut", preferences.moneyOut())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }
}
