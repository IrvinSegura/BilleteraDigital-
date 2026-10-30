package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
class JournalEntryPersistenceAdapter implements JournalEntryRepository {

    private final JournalEntryJpaRepository journalEntryJpaRepository;
    private final PostingJpaRepository postingJpaRepository;

    JournalEntryPersistenceAdapter(JournalEntryJpaRepository journalEntryJpaRepository,
            PostingJpaRepository postingJpaRepository) {
        this.journalEntryJpaRepository = journalEntryJpaRepository;
        this.postingJpaRepository = postingJpaRepository;
    }

    @Override
    public void save(JournalEntry entry) {
        // The entry goes first so the postings' foreign key is satisfied when Hibernate flushes in order
        journalEntryJpaRepository.save(JournalEntryEntity.fromDomain(entry));
        postingJpaRepository.saveAll(entry.postings().stream()
                .map(posting -> PostingEntity.fromDomain(entry.id(), posting))
                .toList());
    }

    @Override
    public MovementSlice findMovements(UUID accountId, int page, int size) {
        Slice<MovementRow> slice = postingJpaRepository.findMovements(accountId, PageRequest.of(page, size));
        return new MovementSlice(slice.getContent().stream().map(MovementRow::toDomain).toList(), slice.hasNext());
    }
}
