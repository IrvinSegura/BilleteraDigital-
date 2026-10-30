package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Insert-only: {@link Immutable} stops Hibernate from issuing updates, and {@code isNew} always being true makes
 * {@code save} insert without a prior select (a second save of the same id fails, as it should).
 */
@Entity
@Immutable
@Table(name = "journal_entries")
class JournalEntryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 100)
    private String reference;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected JournalEntryEntity() {
    }

    static JournalEntryEntity fromDomain(JournalEntry entry) {
        JournalEntryEntity entity = new JournalEntryEntity();
        entity.id = entry.id();
        entity.type = entry.type();
        entity.reference = entry.reference();
        entity.postedAt = entry.postedAt();
        return entity;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
