-- Results of the ledger reconciliation job: each run checks cached balances against postings, that every journal
-- entry balances, and that assets equal liabilities per currency. Runs are evidence, so they are append-only.
CREATE TABLE ledger_reconciliation_runs (
    id                UUID        PRIMARY KEY,
    trigger_type      VARCHAR(20) NOT NULL,
    -- Administrator who started a manual run; null for scheduled runs
    triggered_by      UUID,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ NOT NULL,
    accounts_checked  BIGINT      NOT NULL,
    entries_checked   BIGINT      NOT NULL,
    discrepancy_count INTEGER     NOT NULL,
    CONSTRAINT ck_ledger_reconciliation_runs_trigger_type CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_ledger_reconciliation_runs_discrepancy_count CHECK (discrepancy_count >= 0)
);

CREATE INDEX ix_ledger_reconciliation_runs_started_at ON ledger_reconciliation_runs (started_at DESC);

CREATE TABLE ledger_reconciliation_discrepancies (
    id             UUID        PRIMARY KEY,
    run_id         UUID        NOT NULL,
    kind           VARCHAR(30) NOT NULL,
    -- Account or journal entry that failed the check; null for per-currency totals
    subject_id     UUID,
    currency       VARCHAR(3),
    expected_minor BIGINT      NOT NULL,
    actual_minor   BIGINT      NOT NULL,
    CONSTRAINT fk_ledger_reconciliation_discrepancies_run
        FOREIGN KEY (run_id) REFERENCES ledger_reconciliation_runs (id),
    CONSTRAINT ck_ledger_reconciliation_discrepancies_kind
        CHECK (kind IN ('ACCOUNT_BALANCE', 'UNBALANCED_ENTRY', 'TRIAL_BALANCE'))
);

CREATE INDEX ix_ledger_reconciliation_discrepancies_run_id ON ledger_reconciliation_discrepancies (run_id);

CREATE TRIGGER trg_ledger_reconciliation_runs_append_only
    BEFORE UPDATE OR DELETE ON ledger_reconciliation_runs
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER trg_ledger_reconciliation_discrepancies_append_only
    BEFORE UPDATE OR DELETE ON ledger_reconciliation_discrepancies
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON ledger_reconciliation_runs, '
            'ledger_reconciliation_discrepancies FROM %I', app_role);
    END IF;
END
$$;
