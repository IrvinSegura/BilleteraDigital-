-- Withdrawals now wait for the payout provider: the money leaves the wallet into PAYOUTS_IN_TRANSIT when requested
-- (journal_entry_id), and a second entry (settlement_entry_id) either pays it out or gives it back. Existing
-- withdrawals were paid in a single entry when they were made, so they have no second one.
ALTER TABLE withdrawals
    ADD COLUMN status              VARCHAR(20)  NOT NULL DEFAULT 'PAID',
    -- The payout's destination (accounts module); null for USD and for withdrawals made before bank accounts
    ADD COLUMN bank_account_id     UUID,
    ADD COLUMN provider_reference  VARCHAR(255),
    ADD COLUMN settlement_entry_id UUID,
    ADD COLUMN failure_reason      VARCHAR(50),
    ADD COLUMN closed_at           TIMESTAMPTZ,
    ADD COLUMN version             BIGINT       NOT NULL DEFAULT 0;

UPDATE withdrawals SET closed_at = created_at;

ALTER TABLE withdrawals
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN version DROP DEFAULT,
    ADD CONSTRAINT ck_withdrawals_status CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    ADD CONSTRAINT ck_withdrawals_closed CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    ADD CONSTRAINT ck_withdrawals_pending_unsettled CHECK (status <> 'PENDING' OR settlement_entry_id IS NULL),
    ADD CONSTRAINT ck_withdrawals_failed_has_reason CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    -- A failed payout always gave the money back to the wallet
    ADD CONSTRAINT ck_withdrawals_failed_returned CHECK (status <> 'FAILED' OR settlement_entry_id IS NOT NULL),
    ADD CONSTRAINT uk_withdrawals_settlement_entry UNIQUE (settlement_entry_id);

-- Serves the payout job, which looks only at withdrawals still waiting for the provider
CREATE INDEX ix_withdrawals_pending_created_at ON withdrawals (created_at) WHERE status = 'PENDING';
