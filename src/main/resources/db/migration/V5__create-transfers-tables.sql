-- Users and wallets are referenced by id only (no foreign keys across modules); the money itself lives in the
-- ledger, linked through journal_entry_id.
CREATE TABLE transfers (
    id                    UUID         PRIMARY KEY,
    sender_id             UUID         NOT NULL,
    source_wallet_id      UUID         NOT NULL,
    recipient_id          UUID         NOT NULL,
    -- Snapshot of the address the sender typed, shown on their receipt even if the recipient changes it later
    recipient_email       VARCHAR(254) NOT NULL,
    destination_wallet_id UUID         NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    description           VARCHAR(140),
    journal_entry_id      UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_transfers_journal_entry UNIQUE (journal_entry_id),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_transfers_different_parties CHECK (sender_id <> recipient_id)
);

CREATE TABLE deposits (
    id                  UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL,
    wallet_id           UUID        NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    balance_after_minor BIGINT      NOT NULL,
    journal_entry_id    UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_deposits_journal_entry UNIQUE (journal_entry_id),
    CONSTRAINT ck_deposits_amount_positive CHECK (amount_minor > 0)
);

-- One row per successful money-moving request. The primary key is what makes concurrent retries safe: the second
-- insert with the same key waits for the first transaction and then fails, so only one of them moves money.
CREATE TABLE idempotency_keys (
    user_id         UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    resource_id     UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, idempotency_key)
);
