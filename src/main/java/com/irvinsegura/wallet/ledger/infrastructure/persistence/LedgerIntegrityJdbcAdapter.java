package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Discrepancy;
import com.irvinsegura.wallet.ledger.domain.port.LedgerIntegrityChecks;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL: the checks are set-based aggregations over all postings, which the database does in one pass.
 */
@Repository
class LedgerIntegrityJdbcAdapter implements LedgerIntegrityChecks {

    // Arbitrary but fixed key shared by every instance; transaction-scoped, so a crash can never leave it held
    private static final long RECONCILIATION_LOCK_KEY = 0x4C45444745520001L;

    private final JdbcClient jdbcClient;

    LedgerIntegrityJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean tryLockForReconciliation() {
        return jdbcClient.sql("SELECT pg_try_advisory_xact_lock(:key)")
                .param("key", RECONCILIATION_LOCK_KEY)
                .query(Boolean.class)
                .single();
    }

    @Override
    public long countAccounts() {
        return jdbcClient.sql("SELECT count(*) FROM ledger_accounts").query(Long.class).single();
    }

    @Override
    public long countEntries() {
        return jdbcClient.sql("SELECT count(*) FROM journal_entries").query(Long.class).single();
    }

    @Override
    public List<Discrepancy> findBalanceMismatches(int limit) {
        // A posting on the account's normal side (debit for assets and expenses, credit for the rest) raises it
        return jdbcClient.sql("""
                        SELECT id, currency, actual, expected
                        FROM (SELECT a.id, a.currency, a.balance_minor AS actual,
                                     COALESCE(SUM(CASE WHEN (a.type IN ('ASSET', 'EXPENSE')) = (p.side = 'DEBIT')
                                                       THEN p.amount_minor ELSE -p.amount_minor END), 0) AS expected
                              FROM ledger_accounts a
                              LEFT JOIN postings p ON p.account_id = a.id
                              GROUP BY a.id, a.currency, a.balance_minor) net
                        WHERE actual <> expected
                        ORDER BY id
                        LIMIT :limit""")
                .param("limit", limit)
                .query((rs, rowNum) -> new Discrepancy(Discrepancy.Kind.ACCOUNT_BALANCE,
                        rs.getObject("id", UUID.class), rs.getString("currency"), rs.getLong("expected"),
                        rs.getLong("actual")))
                .list();
    }

    @Override
    public List<Discrepancy> findUnbalancedEntries(int limit) {
        return jdbcClient.sql("""
                        SELECT e.id, MIN(p.currency) AS currency,
                               COALESCE(SUM(p.amount_minor) FILTER (WHERE p.side = 'DEBIT'), 0) AS debits,
                               COALESCE(SUM(p.amount_minor) FILTER (WHERE p.side = 'CREDIT'), 0) AS credits
                        FROM journal_entries e
                        LEFT JOIN postings p ON p.journal_entry_id = e.id
                        GROUP BY e.id
                        HAVING COUNT(p.id) < 2
                            OR COUNT(DISTINCT p.currency) <> 1
                            OR COALESCE(SUM(p.amount_minor) FILTER (WHERE p.side = 'DEBIT'), 0)
                               <> COALESCE(SUM(p.amount_minor) FILTER (WHERE p.side = 'CREDIT'), 0)
                        ORDER BY e.id
                        LIMIT :limit""")
                .param("limit", limit)
                .query((rs, rowNum) -> new Discrepancy(Discrepancy.Kind.UNBALANCED_ENTRY,
                        rs.getObject("id", UUID.class), rs.getString("currency"), rs.getLong("debits"),
                        rs.getLong("credits")))
                .list();
    }

    @Override
    public List<Discrepancy> findTrialBalanceGaps() {
        // Every entry moves the same amount on both sides, so per currency the debit-normal balances (what the
        // platform holds plus what it spent) must equal the credit-normal ones (what it owes, its capital and what
        // it earned)
        return jdbcClient.sql("""
                        SELECT currency,
                               COALESCE(SUM(balance_minor) FILTER (WHERE type IN ('ASSET', 'EXPENSE')), 0) AS debits,
                               COALESCE(SUM(balance_minor) FILTER (WHERE type IN ('LIABILITY', 'EQUITY', 'REVENUE')), 0)
                                   AS credits
                        FROM ledger_accounts
                        GROUP BY currency
                        HAVING COALESCE(SUM(balance_minor) FILTER (WHERE type IN ('ASSET', 'EXPENSE')), 0)
                               <> COALESCE(SUM(balance_minor) FILTER (WHERE type IN ('LIABILITY', 'EQUITY', 'REVENUE')), 0)
                        ORDER BY currency""")
                .query((rs, rowNum) -> new Discrepancy(Discrepancy.Kind.TRIAL_BALANCE, null,
                        rs.getString("currency"), rs.getLong("debits"), rs.getLong("credits")))
                .list();
    }
}
