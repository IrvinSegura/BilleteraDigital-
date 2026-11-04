package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Side;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    // Every criterion is always bound, open-ended ones with values that match anything, so no parameter is ever null
    // and Postgres never has to guess its type
    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.MovementRow(
                e.id, e.type, e.reference, p.side, p.amountMinor, p.currency, p.balanceAfterMinor, e.postedAt)
            from PostingEntity p join JournalEntryEntity e on e.id = p.journalEntryId
            where p.accountId = :accountId
              and e.postedAt >= :from and e.postedAt < :to
              and p.side in :sides
              and (:anyType = true or e.type in :types)
              and (:anyReference = true or lower(e.reference) like :referencePattern)
            order by p.postingNumber desc""")
    Slice<MovementRow> findMovements(@Param("accountId") UUID accountId, @Param("from") Instant from,
            @Param("to") Instant to, @Param("sides") Collection<Side> sides, @Param("anyType") boolean anyType,
            @Param("types") Collection<String> types, @Param("anyReference") boolean anyReference,
            @Param("referencePattern") String referencePattern, Pageable pageable);

    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.MovementRow(
                e.id, e.type, e.reference, p.side, p.amountMinor, p.currency, p.balanceAfterMinor, e.postedAt)
            from PostingEntity p join JournalEntryEntity e on e.id = p.journalEntryId
            where p.accountId = :accountId and p.journalEntryId = :journalEntryId""")
    List<MovementRow> findMovement(@Param("accountId") UUID accountId,
            @Param("journalEntryId") UUID journalEntryId);

    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.SideTotalRow(p.side, sum(p.amountMinor))
            from PostingEntity p join JournalEntryEntity e on e.id = p.journalEntryId
            where p.accountId = :accountId and e.postedAt >= :from and e.postedAt < :to
            group by p.side""")
    List<SideTotalRow> sumBySide(@Param("accountId") UUID accountId, @Param("from") Instant from,
            @Param("to") Instant to);

    // Walks the account's postings from the newest (ix_postings_account_posting_number), so a recent instant reads
    // only the few postings after it
    @Query(value = """
            SELECT p.balance_after_minor
            FROM postings p JOIN journal_entries e ON e.id = p.journal_entry_id
            WHERE p.account_id = :accountId AND e.posted_at < :before
            ORDER BY p.posting_number DESC
            LIMIT 1""", nativeQuery = true)
    Optional<Long> balanceBefore(@Param("accountId") UUID accountId, @Param("before") Instant before);

    // The last posting of each local day. The day comes back as text, so its type doesn't depend on the driver.
    @Query(value = """
            SELECT DISTINCT ON (t.day) t.day AS day, t.balance_after_minor AS balanceMinor
            FROM (
                SELECT to_char(e.posted_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day, p.balance_after_minor,
                       p.posting_number
                FROM postings p JOIN journal_entries e ON e.id = p.journal_entry_id
                WHERE p.account_id = :accountId AND e.posted_at >= :from AND e.posted_at < :to
            ) t
            ORDER BY t.day, t.posting_number DESC""", nativeQuery = true)
    List<DayCloseRow> closingBalances(@Param("accountId") UUID accountId, @Param("from") Instant from,
            @Param("to") Instant to, @Param("zone") String zone);

    interface DayCloseRow {

        String getDay();

        long getBalanceMinor();
    }
}
