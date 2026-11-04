-- What a person concluded about a movement the application could not match. Closing one records the decision and who
-- took it; it posts nothing, so any correction to the ledger stays a separate, visible step.
ALTER TABLE provider_balance_transactions
    ADD COLUMN resolved_by       UUID,
    ADD COLUMN resolution_reason VARCHAR(200),
    ADD COLUMN resolved_at       TIMESTAMPTZ,
    DROP CONSTRAINT ck_provider_balance_transactions_status,
    ADD CONSTRAINT ck_provider_balance_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'UNMATCHED',
        'RESOLVED')),
    ADD CONSTRAINT ck_provider_balance_transactions_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL)
        AND (resolved_at IS NULL) = (resolved_by IS NULL)
        AND (resolved_at IS NULL) = (resolution_reason IS NULL));
