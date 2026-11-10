package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import com.irvinsegura.wallet.ledger.domain.model.Movement;
import com.irvinsegura.wallet.ledger.domain.model.Side;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
class JournalEntryPersistenceAdapter implements JournalEntryRepository {

    // Bounds for an open-ended period, within what a Postgres timestamptz holds
    private static final Instant EARLIEST = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

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
    public boolean existsByTypeAndReference(String type, String reference) {
        return journalEntryJpaRepository.existsByTypeAndReference(type, reference);
    }

    @Override
    public MovementSlice findMovements(UUID accountId, int page, int size) {
        Slice<MovementRow> slice = postingJpaRepository.findMovements(accountId, PageRequest.of(page, size));
        return new MovementSlice(slice.getContent().stream().map(MovementRow::toDomain).toList(), slice.hasNext());
    }

    @Override
    public MovementSlice findMovements(UUID accountId, MovementQuery query, int page, int size) {
        Set<Side> sides = query.side() == null ? EnumSet.allOf(Side.class) : EnumSet.of(query.side());
        boolean anyType = query.types().isEmpty();
        boolean anyReference = query.referencePrefix() == null && query.references() == null;
        boolean noReferences = query.references() == null || query.references().isEmpty();
        Slice<MovementRow> slice = postingJpaRepository.findMovements(accountId,
                query.from() == null ? EARLIEST : query.from(), query.to() == null ? LATEST : query.to(), sides,
                anyType, anyType ? Set.of("") : query.types(), anyReference,
                query.referencePrefix() == null ? "" : query.referencePrefix().toLowerCase(Locale.ROOT) + "%",
                noReferences ? Set.of("") : query.references(),
                PageRequest.of(page, size));
        return new MovementSlice(slice.getContent().stream().map(MovementRow::toDomain).toList(), slice.hasNext());
    }

    @Override
    public Optional<Movement> findMovement(UUID accountId, UUID journalEntryId) {
        // An entry posts to an account once, so there is at most one row
        return postingJpaRepository.findMovement(accountId, journalEntryId).stream().findFirst()
                .map(MovementRow::toDomain);
    }

    @Override
    public SideTotals sumBySide(UUID accountId, Instant from, Instant to) {
        long debit = 0;
        long credit = 0;
        for (SideTotalRow row : postingJpaRepository.sumBySide(accountId, from, to)) {
            if (row.side() == Side.DEBIT) {
                debit = row.totalMinor();
            } else {
                credit = row.totalMinor();
            }
        }
        return new SideTotals(debit, credit);
    }

    @Override
    public Optional<Long> balanceBefore(UUID accountId, Instant before) {
        return postingJpaRepository.balanceBefore(accountId, before);
    }

    @Override
    public List<DayClose> closingBalances(UUID accountId, Instant from, Instant to, ZoneId zone) {
        return postingJpaRepository.closingBalances(accountId, from, to, zone.getId()).stream()
                .map(row -> new DayClose(LocalDate.parse(row.getDay()), row.getBalanceMinor()))
                .toList();
    }
}
