-- The entry that recorded a payment's money arriving (into PROVIDER_CLEARING, or SETTLEMENT with the simulated
-- provider). Payments collected before it existed have none, and their refunds post nothing.
ALTER TABLE payments ADD COLUMN received_entry_id UUID;

-- Serves matching the provider's balance activity to payments
CREATE INDEX ix_payments_provider_payment_id ON payments (provider_payment_id) WHERE provider_payment_id IS NOT NULL;

-- Every movement of the provider's balance (charges, refunds, fees, payouts to the partner bank), read from the
-- provider and recorded in the ledger once. Rows the application cannot match stay for a person to look at.
CREATE TABLE provider_balance_transactions (
    provider            VARCHAR(20)  NOT NULL,
    id                  VARCHAR(255) NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    -- The provider's own type, e.g. Stripe's charge, payout or adjustment
    provider_type       VARCHAR(50)  NOT NULL,
    -- Signed, as the provider reports it: negative amounts left the provider's balance
    amount_minor        BIGINT       NOT NULL,
    fee_minor           BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    -- The collected payment a charge or refund belongs to (a Stripe PaymentIntent)
    provider_payment_id VARCHAR(255),
    status              VARCHAR(20)  NOT NULL,
    note                VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    settled_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL,
    CONSTRAINT pk_provider_balance_transactions PRIMARY KEY (provider, id),
    CONSTRAINT ck_provider_balance_transactions_type CHECK (type IN ('CHARGE', 'REFUND', 'PAYOUT', 'PAYOUT_REVERSAL',
        'FEE', 'OTHER')),
    CONSTRAINT ck_provider_balance_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'UNMATCHED')),
    CONSTRAINT ck_provider_balance_transactions_fee CHECK (fee_minor >= 0),
    CONSTRAINT ck_provider_balance_transactions_settled CHECK ((status = 'PENDING') = (settled_at IS NULL))
);

-- Serves the sync job, which posts pending rows oldest first and resumes reading after the newest one
CREATE INDEX ix_provider_balance_transactions_pending ON provider_balance_transactions (created_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_provider_balance_transactions_created_at ON provider_balance_transactions (provider, created_at DESC);
