-- Double-entry ledger. Amounts are minor units (cents); balances are kept in each account's normal side, so they
-- are never negative. No foreign keys point to other modules' tables, so the ledger can be extracted later.
CREATE TABLE ledger_accounts (
    id            UUID        PRIMARY KEY,
    type          VARCHAR(20) NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    balance_minor BIGINT      NOT NULL,
    -- Set only for platform accounts, e.g. SETTLEMENT; user accounts are referenced by their owner module
    system_code   VARCHAR(30),
    created_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL,
    CONSTRAINT ck_ledger_accounts_type CHECK (type IN ('ASSET', 'LIABILITY')),
    CONSTRAINT ck_ledger_accounts_balance_non_negative CHECK (balance_minor >= 0),
    CONSTRAINT uk_ledger_accounts_system_code_currency UNIQUE (system_code, currency)
);

CREATE TABLE journal_entries (
    id        UUID         PRIMARY KEY,
    type      VARCHAR(30)  NOT NULL,
    reference VARCHAR(100),
    posted_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE postings (
    id                  UUID        PRIMARY KEY,
    -- Insertion order; per account it matches the order in which postings were applied, because accounts are
    -- locked while posting
    posting_number      BIGINT      GENERATED ALWAYS AS IDENTITY,
    journal_entry_id    UUID        NOT NULL,
    account_id          UUID        NOT NULL,
    side                VARCHAR(6)  NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    balance_after_minor BIGINT      NOT NULL,
    CONSTRAINT uk_postings_posting_number UNIQUE (posting_number),
    CONSTRAINT uk_postings_entry_account UNIQUE (journal_entry_id, account_id),
    CONSTRAINT fk_postings_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id),
    CONSTRAINT fk_postings_account FOREIGN KEY (account_id) REFERENCES ledger_accounts (id),
    CONSTRAINT ck_postings_side CHECK (side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_postings_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_postings_balance_after_non_negative CHECK (balance_after_minor >= 0)
);

-- Serves statements: one account's postings, newest first
CREATE INDEX ix_postings_account_posting_number ON postings (account_id, posting_number DESC);

-- Journal entries and postings are append-only, for every database user. Corrections are reversing entries.
CREATE FUNCTION reject_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger table % is append-only', TG_TABLE_NAME;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER trg_postings_append_only
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

-- TRUNCATE skips row triggers, so the application role also loses the privileges outright
DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON journal_entries, postings FROM %I', app_role);
    END IF;
END
$$;

-- Settlement accounts: the platform's money at the partner bank. Their currencies are the supported currencies.
INSERT INTO ledger_accounts (id, type, currency, balance_minor, system_code, created_at, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'ASSET', 'MXN', 0, 'SETTLEMENT', now(), 0),
       ('00000000-0000-0000-0000-000000000002', 'ASSET', 'USD', 0, 'SETTLEMENT', now(), 0);
