-- Deposits now wait for the payment provider: created pending, credited (and given a ledger entry) only when the
-- payment succeeds. Existing deposits were credited when they were made.
ALTER TABLE deposits
    ADD COLUMN status              VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN checkout_url        VARCHAR(2048),
    ADD COLUMN checkout_expires_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason    VARCHAR(50),
    ADD COLUMN closed_at           TIMESTAMPTZ,
    ADD COLUMN version             BIGINT        NOT NULL DEFAULT 0,
    ALTER COLUMN balance_after_minor DROP NOT NULL,
    ALTER COLUMN journal_entry_id DROP NOT NULL;

UPDATE deposits SET closed_at = created_at;

ALTER TABLE deposits
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN version DROP DEFAULT,
    ADD CONSTRAINT ck_deposits_status CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'FAILED', 'REJECTED')),
    -- Money reaches the wallet, and the ledger, only for completed deposits
    ADD CONSTRAINT ck_deposits_completed_has_entry CHECK ((status = 'COMPLETED')
        = (journal_entry_id IS NOT NULL AND balance_after_minor IS NOT NULL)),
    ADD CONSTRAINT ck_deposits_closed CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    ADD CONSTRAINT ck_deposits_rejected_has_reason CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL));
