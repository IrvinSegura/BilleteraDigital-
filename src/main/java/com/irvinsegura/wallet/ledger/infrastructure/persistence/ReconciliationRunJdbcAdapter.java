package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Discrepancy;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import com.irvinsegura.wallet.ledger.domain.port.ReconciliationRunRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReconciliationRunJdbcAdapter implements ReconciliationRunRepository {

    private final JdbcClient jdbcClient;

    ReconciliationRunJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(ReconciliationRun run) {
        jdbcClient.sql("""
                        INSERT INTO ledger_reconciliation_runs (id, trigger_type, triggered_by, started_at, finished_at,
                            accounts_checked, entries_checked, discrepancy_count)
                        VALUES (:id, :trigger, :triggeredBy, :startedAt, :finishedAt, :accounts, :entries, :count)""")
                .param("id", run.id())
                .param("trigger", run.trigger().name())
                .param("triggeredBy", run.triggeredBy())
                .param("startedAt", OffsetDateTime.ofInstant(run.startedAt(), ZoneOffset.UTC))
                .param("finishedAt", OffsetDateTime.ofInstant(run.finishedAt(), ZoneOffset.UTC))
                .param("accounts", run.accountsChecked())
                .param("entries", run.entriesChecked())
                .param("count", run.discrepancies().size())
                .update();
        for (Discrepancy discrepancy : run.discrepancies()) {
            jdbcClient.sql("""
                            INSERT INTO ledger_reconciliation_discrepancies (id, run_id, kind, subject_id, currency,
                                expected_minor, actual_minor)
                            VALUES (:id, :runId, :kind, :subjectId, :currency, :expected, :actual)""")
                    .param("id", UUID.randomUUID())
                    .param("runId", run.id())
                    .param("kind", discrepancy.kind().name())
                    .param("subjectId", discrepancy.subjectId())
                    .param("currency", discrepancy.currency())
                    .param("expected", discrepancy.expected())
                    .param("actual", discrepancy.actual())
                    .update();
        }
    }

    @Override
    public List<ReconciliationRun> findRecent(int limit) {
        List<RunRow> runs = jdbcClient.sql("""
                        SELECT id, trigger_type, triggered_by, started_at, finished_at, accounts_checked,
                               entries_checked
                        FROM ledger_reconciliation_runs
                        ORDER BY started_at DESC, id
                        LIMIT :limit""")
                .param("limit", limit)
                .query(ReconciliationRunJdbcAdapter::mapRun)
                .list();
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Discrepancy>> discrepancies = jdbcClient.sql("""
                        SELECT run_id, kind, subject_id, currency, expected_minor, actual_minor
                        FROM ledger_reconciliation_discrepancies
                        WHERE run_id IN (:runIds)
                        ORDER BY kind, subject_id, currency""")
                .param("runIds", runs.stream().map(RunRow::id).toList())
                .query((rs, rowNum) -> Map.entry(rs.getObject("run_id", UUID.class), new Discrepancy(
                        Discrepancy.Kind.valueOf(rs.getString("kind")), rs.getObject("subject_id", UUID.class),
                        rs.getString("currency"), rs.getLong("expected_minor"), rs.getLong("actual_minor"))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return runs.stream()
                .map(row -> row.toDomain(discrepancies.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Override
    public Optional<LatestRun> findLatest() {
        return jdbcClient.sql("""
                        SELECT finished_at, discrepancy_count
                        FROM ledger_reconciliation_runs
                        ORDER BY started_at DESC
                        LIMIT 1""")
                .query((rs, rowNum) -> new LatestRun(rs.getObject("finished_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("discrepancy_count")))
                .optional();
    }

    private static RunRow mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new RunRow(rs.getObject("id", UUID.class),
                ReconciliationRun.Trigger.valueOf(rs.getString("trigger_type")),
                rs.getObject("triggered_by", UUID.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getLong("accounts_checked"),
                rs.getLong("entries_checked"));
    }

    private record RunRow(UUID id, ReconciliationRun.Trigger trigger, UUID triggeredBy, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, long accountsChecked, long entriesChecked) {

        ReconciliationRun toDomain(List<Discrepancy> discrepancies) {
            return new ReconciliationRun(id, trigger, triggeredBy, startedAt.toInstant(), finishedAt.toInstant(),
                    accountsChecked, entriesChecked, discrepancies);
        }
    }
}
