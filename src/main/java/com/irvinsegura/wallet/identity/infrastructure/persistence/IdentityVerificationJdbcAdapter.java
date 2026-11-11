package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.DocumentSide;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationStatus;
import com.irvinsegura.wallet.identity.domain.port.IdentityVerificationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC. The pictures are only read one at a time, by {@link #image}, so lists never load them.
 */
@Repository
class IdentityVerificationJdbcAdapter implements IdentityVerificationRepository {

    private static final String COLUMNS = """
            id, user_id, status, encrypted_data, curp_fingerprint, voter_key_fingerprint, curp_last_four,
            names_match_curp, name_matches_account, submitted_at, reviewed_at, reviewed_by, rejection_reason""";

    private final JdbcClient jdbcClient;

    IdentityVerificationJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(IdentityVerification verification, Map<DocumentSide, byte[]> encryptedImages) {
        jdbcClient.sql("""
                        INSERT INTO identity_verifications (id, user_id, status, encrypted_data, curp_fingerprint,
                            voter_key_fingerprint, curp_last_four, names_match_curp, name_matches_account,
                            front_image, back_image, selfie_image, submitted_at)
                        VALUES (:id, :userId, :status, :data, :curp, :voterKey, :lastFour, :namesMatchCurp,
                            :nameMatchesAccount, :front, :back, :selfie, :submittedAt)""")
                .param("id", verification.id())
                .param("userId", verification.userId())
                .param("status", verification.status().name())
                .param("data", verification.encryptedData())
                .param("curp", verification.curpFingerprint())
                .param("voterKey", verification.voterKeyFingerprint())
                .param("lastFour", verification.curpLastFour())
                .param("namesMatchCurp", verification.namesMatchCurp())
                .param("nameMatchesAccount", verification.nameMatchesAccount())
                .param("front", encryptedImages.get(DocumentSide.FRONT))
                .param("back", encryptedImages.get(DocumentSide.BACK))
                .param("selfie", encryptedImages.get(DocumentSide.SELFIE))
                .param("submittedAt", utc(verification.submittedAt()))
                .update();
    }

    @Override
    public void updateReview(IdentityVerification verification) {
        jdbcClient.sql("""
                        UPDATE identity_verifications
                        SET status = :status, reviewed_at = :reviewedAt, reviewed_by = :reviewedBy,
                            rejection_reason = :reason
                        WHERE id = :id""")
                .param("id", verification.id())
                .param("status", verification.status().name())
                .param("reviewedAt", verification.reviewedAt() == null ? null : utc(verification.reviewedAt()))
                .param("reviewedBy", verification.reviewedBy())
                .param("reason", verification.rejectionReason())
                .update();
    }

    @Override
    public Optional<IdentityVerification> findById(UUID id) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM identity_verifications WHERE id = :id")
                .param("id", id).query(IdentityVerificationJdbcAdapter::map).optional();
    }

    @Override
    public Optional<IdentityVerification> findByIdForUpdate(UUID id) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM identity_verifications WHERE id = :id FOR UPDATE")
                .param("id", id).query(IdentityVerificationJdbcAdapter::map).optional();
    }

    @Override
    public Optional<IdentityVerification> findLatestByUser(UUID userId) {
        return jdbcClient.sql("SELECT " + COLUMNS + """
                         FROM identity_verifications WHERE user_id = :userId
                        ORDER BY submitted_at DESC LIMIT 1""")
                .param("userId", userId).query(IdentityVerificationJdbcAdapter::map).optional();
    }

    @Override
    public int countSubmittedSince(UUID userId, Instant since) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM identity_verifications
                        WHERE user_id = :userId AND submitted_at >= :since""")
                .param("userId", userId).param("since", utc(since)).query(Integer.class).single();
    }

    @Override
    public boolean documentInUse(String curpFingerprint, String voterKeyFingerprint, UUID exceptUserId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (SELECT 1 FROM identity_verifications
                        WHERE status <> 'REJECTED' AND user_id <> :userId
                            AND (curp_fingerprint = :curp OR voter_key_fingerprint = :voterKey))""")
                .param("userId", exceptUserId)
                .param("curp", curpFingerprint)
                .param("voterKey", voterKeyFingerprint)
                .query(Boolean.class).single();
    }

    @Override
    public long countPending() {
        return jdbcClient.sql("SELECT count(*) FROM identity_verifications WHERE status = 'PENDING_REVIEW'")
                .query(Long.class).single();
    }

    @Override
    public boolean isVerified(UUID userId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (SELECT 1 FROM identity_verifications
                        WHERE user_id = :userId AND status = 'VERIFIED')""")
                .param("userId", userId).query(Boolean.class).single();
    }

    @Override
    public VerificationSlice page(IdentityVerificationStatus status, int page, int size) {
        // The queue is worked oldest first; the rest reads as a history, newest first
        String order = status == IdentityVerificationStatus.PENDING_REVIEW ? "ASC" : "DESC";
        List<IdentityVerification> rows = jdbcClient.sql("SELECT " + COLUMNS + """
                         FROM identity_verifications
                        WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                        ORDER BY submitted_at\s""" + order + ", id LIMIT :limit OFFSET :offset")
                .param("status", status == null ? null : status.name())
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query(IdentityVerificationJdbcAdapter::map).list();
        boolean hasNext = rows.size() > size;
        return new VerificationSlice(hasNext ? rows.subList(0, size) : rows, hasNext);
    }

    @Override
    public Optional<byte[]> image(UUID id, DocumentSide side) {
        String column = switch (side) {
            case FRONT -> "front_image";
            case BACK -> "back_image";
            case SELFIE -> "selfie_image";
        };
        return jdbcClient.sql("SELECT " + column + " FROM identity_verifications WHERE id = :id")
                .param("id", id).query((rs, row) -> rs.getBytes(1)).optional();
    }

    private static IdentityVerification map(ResultSet rs, int row) throws SQLException {
        OffsetDateTime reviewedAt = rs.getObject("reviewed_at", OffsetDateTime.class);
        return IdentityVerification.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                IdentityVerificationStatus.valueOf(rs.getString("status")),
                rs.getString("encrypted_data"),
                rs.getString("curp_fingerprint"),
                rs.getString("voter_key_fingerprint"),
                rs.getString("curp_last_four"),
                rs.getBoolean("names_match_curp"),
                rs.getBoolean("name_matches_account"),
                rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getObject("reviewed_by", UUID.class),
                rs.getString("rejection_reason"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
