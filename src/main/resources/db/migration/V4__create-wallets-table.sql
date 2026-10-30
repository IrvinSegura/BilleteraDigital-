-- owner_id and ledger_account_id point to other modules' tables (users, ledger_accounts) without foreign keys,
-- so module boundaries hold in the database too; the application keeps them consistent.
CREATE TABLE wallets (
    id                UUID        PRIMARY KEY,
    owner_id          UUID        NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    ledger_account_id UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL,
    -- One wallet per currency per user; the index also serves "list my wallets"
    CONSTRAINT uk_wallets_owner_currency UNIQUE (owner_id, currency),
    CONSTRAINT uk_wallets_ledger_account UNIQUE (ledger_account_id),
    CONSTRAINT ck_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN'))
);
