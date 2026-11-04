package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.TwoFactor;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.TwoFactorRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: one row per user written with an upsert, and recovery codes used with a conditional update.
 */
@Repository
class TwoFactorJdbcAdapter implements TwoFactorRepository {

    private static final String SELECT = """
            SELECT user_id, encrypted_secret, status, last_used_step, created_at, enabled_at, version
            FROM user_two_factor WHERE user_id = :userId""";

    private final JdbcClient jdbcClient;

    TwoFactorJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<TwoFactor> findByUserId(UserId userId) {
        return jdbcClient.sql(SELECT).param("userId", userId.value()).query(TwoFactorJdbcAdapter::map).optional();
    }

    @Override
    public Optional<TwoFactor> findByUserIdForUpdate(UserId userId) {
        return jdbcClient.sql(SELECT + " FOR UPDATE")
                .param("userId", userId.value())
                .query(TwoFactorJdbcAdapter::map)
                .optional();
    }

    @Override
    public void save(TwoFactor twoFactor) {
        jdbcClient.sql("""
                        INSERT INTO user_two_factor (user_id, encrypted_secret, status, last_used_step, created_at,
                            enabled_at, version)
                        VALUES (:userId, :secret, :status, :lastUsedStep, :createdAt, :enabledAt, 0)
                        ON CONFLICT (user_id) DO UPDATE
                        SET status = EXCLUDED.status, last_used_step = EXCLUDED.last_used_step,
                            enabled_at = EXCLUDED.enabled_at, version = user_two_factor.version + 1""")
                .param("userId", twoFactor.userId().value())
                .param("secret", twoFactor.encryptedSecret())
                .param("status", twoFactor.status().name())
                .param("lastUsedStep", twoFactor.lastUsedStep())
                .param("createdAt", utc(twoFactor.createdAt()))
                .param("enabledAt", twoFactor.enabledAt() == null ? null : utc(twoFactor.enabledAt()))
                .update();
    }

    @Override
    public void delete(UserId userId) {
        jdbcClient.sql("DELETE FROM user_recovery_codes WHERE user_id = :userId")
                .param("userId", userId.value())
                .update();
        jdbcClient.sql("DELETE FROM user_two_factor WHERE user_id = :userId").param("userId", userId.value()).update();
    }

    @Override
    public void replaceRecoveryCodes(UserId userId, List<String> codeHashes, Instant now) {
        jdbcClient.sql("DELETE FROM user_recovery_codes WHERE user_id = :userId")
                .param("userId", userId.value())
                .update();
        for (String codeHash : codeHashes) {
            jdbcClient.sql("""
                            INSERT INTO user_recovery_codes (user_id, code_hash, created_at)
                            VALUES (:userId, :codeHash, :createdAt)""")
                    .param("userId", userId.value())
                    .param("codeHash", codeHash)
                    .param("createdAt", utc(now))
                    .update();
        }
    }

    @Override
    public boolean useRecoveryCode(UserId userId, String codeHash, Instant now) {
        return jdbcClient.sql("""
                        UPDATE user_recovery_codes SET used_at = :usedAt
                        WHERE user_id = :userId AND code_hash = :codeHash AND used_at IS NULL""")
                .param("usedAt", utc(now))
                .param("userId", userId.value())
                .param("codeHash", codeHash)
                .update() == 1;
    }

    @Override
    public int countUnusedRecoveryCodes(UserId userId) {
        return jdbcClient.sql("SELECT count(*) FROM user_recovery_codes WHERE user_id = :userId AND used_at IS NULL")
                .param("userId", userId.value())
                .query(Integer.class)
                .single();
    }

    private static TwoFactor map(ResultSet rs, int rowNum) throws SQLException {
        return TwoFactor.restore(new UserId(rs.getObject("user_id", UUID.class)), rs.getString("encrypted_secret"),
                TwoFactor.Status.valueOf(rs.getString("status")), rs.getObject("last_used_step", Long.class),
                instant(rs, "created_at"), instant(rs, "enabled_at"), rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
