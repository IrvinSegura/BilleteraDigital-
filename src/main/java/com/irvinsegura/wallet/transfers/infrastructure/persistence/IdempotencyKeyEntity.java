package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Insert-only. Always inserting (never merging) is what turns a duplicate key into a primary key violation.
 */
@Entity
@Immutable
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity implements Persistable<IdempotencyKeyEntity.Key> {

    @EmbeddedId
    private Key id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    static IdempotencyKeyEntity fromDomain(IdempotencyRecord record) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.id = new Key(record.userId(), record.key());
        entity.requestHash = record.requestHash();
        entity.resourceId = record.resourceId();
        entity.createdAt = record.createdAt();
        return entity;
    }

    IdempotencyRecord toDomain() {
        return new IdempotencyRecord(id.userId(), id.idempotencyKey(), requestHash, resourceId, createdAt);
    }

    @Override
    public Key getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    @Embeddable
    record Key(
            @Column(name = "user_id", nullable = false) UUID userId,
            @Column(name = "idempotency_key", nullable = false, length = 100) String idempotencyKey) {
    }
}
