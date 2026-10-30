package com.irvinsegura.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.accounts.application.OutgoingUsageCleanupService;
import com.irvinsegura.wallet.identity.application.RefreshTokenCleanupService;
import com.irvinsegura.wallet.transfers.application.IdempotencyKeyCleanupService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The jobs only call these services on a schedule, which is disabled in tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CleanupJobsIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdempotencyKeyCleanupService idempotencyKeyCleanup;

    @Autowired
    private OutgoingUsageCleanupService outgoingUsageCleanup;

    @Autowired
    private RefreshTokenCleanupService refreshTokenCleanup;

    @Test
    void idempotencyKeysAreDeletedAfterTheRetentionPeriod() {
        UUID userId = UUID.randomUUID();
        insertIdempotencyKey(userId, "old", Instant.now().minus(Duration.ofHours(25)));
        insertIdempotencyKey(userId, "recent", Instant.now().minus(Duration.ofHours(23)));

        assertThat(idempotencyKeyCleanup.deleteOlderThan(Duration.ofHours(24))).isGreaterThanOrEqualTo(1);

        assertThat(jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_keys WHERE user_id = ?", String.class, userId))
                .containsExactly("recent");
    }

    @Test
    void dailyUsageIsDeletedAfterTheRetentionPeriod() {
        UUID walletId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneId.of("America/Mexico_City"));
        insertUsage(walletId, today.minusDays(8));
        insertUsage(walletId, today.minusDays(7));
        insertUsage(walletId, today);

        outgoingUsageCleanup.deleteOlderThan(Period.ofDays(7));

        assertThat(jdbcTemplate.queryForList(
                "SELECT day FROM wallet_daily_outgoing WHERE wallet_id = ? ORDER BY day", LocalDate.class, walletId))
                .containsExactly(today.minusDays(7), today);
    }

    @Test
    void refreshTokenFamiliesAreDeletedOnlyOnceEveryTokenHasExpired() {
        UUID userId = insertUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID expiredFamily = UUID.randomUUID();
        insertRefreshToken(userId, expiredFamily, now.minus(Duration.ofDays(9)), now.minus(Duration.ofDays(2)));
        insertRefreshToken(userId, expiredFamily, now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(1)));
        // A rotated (expired) token next to a live one: kept, so reusing it still revokes the family
        UUID liveFamily = UUID.randomUUID();
        insertRefreshToken(userId, liveFamily, now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(1)));
        insertRefreshToken(userId, liveFamily, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(6)));

        refreshTokenCleanup.deleteExpiredTokens();

        assertThat(jdbcTemplate.queryForList(
                "SELECT family_id FROM refresh_tokens WHERE user_id = ?", UUID.class, userId))
                .containsExactly(liveFamily, liveFamily);
    }

    private void insertIdempotencyKey(UUID userId, String key, Instant createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, resource_id, created_at)
                        VALUES (?, ?, 'hash', ?, ?)""",
                userId, key, UUID.randomUUID(), Timestamp.from(createdAt));
    }

    private void insertUsage(UUID walletId, LocalDate day) {
        jdbcTemplate.update("INSERT INTO wallet_daily_outgoing (wallet_id, day, amount_minor) VALUES (?, ?, 100)",
                walletId, day);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO users (id, email, password_hash, full_name, role, created_at, version)
                        VALUES (?, ?, 'hash', 'Cleanup Test', 'USER', now(), 0)""",
                userId, "cleanup-" + userId + "@example.com");
        return userId;
    }

    private void insertRefreshToken(UUID userId, UUID familyId, Instant issuedAt, Instant expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, issued_at, expires_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, 0)""",
                UUID.randomUUID(), userId, familyId, UUID.randomUUID().toString(), Timestamp.from(issuedAt),
                Timestamp.from(expiresAt));
    }
}
