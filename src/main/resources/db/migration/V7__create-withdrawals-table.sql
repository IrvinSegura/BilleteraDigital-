CREATE TABLE withdrawals (
    id                  UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL,
    wallet_id           UUID        NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    balance_after_minor BIGINT      NOT NULL,
    journal_entry_id    UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_withdrawals_journal_entry UNIQUE (journal_entry_id),
    CONSTRAINT ck_withdrawals_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_withdrawals_balance_after_non_negative CHECK (balance_after_minor >= 0)
);
