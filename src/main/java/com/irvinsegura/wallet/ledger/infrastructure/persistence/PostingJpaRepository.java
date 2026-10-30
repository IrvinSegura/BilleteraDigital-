package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostingJpaRepository extends JpaRepository<PostingEntity, UUID> {

    // A Slice fetches one extra row to know whether a next page exists, avoiding a count over all postings
    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.MovementRow(
                e.id, e.type, e.reference, p.side, p.amountMinor, p.currency, p.balanceAfterMinor, e.postedAt)
            from PostingEntity p join JournalEntryEntity e on e.id = p.journalEntryId
            where p.accountId = :accountId
            order by p.postingNumber desc""")
    Slice<MovementRow> findMovements(@Param("accountId") UUID accountId, Pageable pageable);
}
