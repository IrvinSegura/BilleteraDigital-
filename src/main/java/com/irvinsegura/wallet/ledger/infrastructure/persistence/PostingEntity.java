package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Posting;
import com.irvinsegura.wallet.ledger.domain.model.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Insert-only, like {@link JournalEntryEntity}. The entry id is a plain column rather than an association so
 * Hibernate never needs a follow-up update to link the rows.
 */
@Entity
@Immutable
@Table(name = "postings")
class PostingEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    // Assigned by the database
    @Column(name = "posting_number", insertable = false, updatable = false)
    private Long postingNumber;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Side side;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after_minor", nullable = false)
    private long balanceAfterMinor;

    protected PostingEntity() {
    }

    static PostingEntity fromDomain(UUID journalEntryId, Posting posting) {
        PostingEntity entity = new PostingEntity();
        entity.id = posting.id();
        entity.journalEntryId = journalEntryId;
        entity.accountId = posting.accountId();
        entity.side = posting.side();
        entity.amountMinor = posting.amount().minorUnits();
        entity.currency = posting.amount().currency().getCurrencyCode();
        entity.balanceAfterMinor = posting.balanceAfter().minorUnits();
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
