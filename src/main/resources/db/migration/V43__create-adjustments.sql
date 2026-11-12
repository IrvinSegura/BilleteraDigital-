-- Administrators settle customers' problems by putting money into a wallet or taking it out. What they put in is an
-- expense (CUSTOMER_ADJUSTMENTS); what they take back, e.g. money credited twice, is revenue (ADJUSTMENT_RECOVERIES),
-- a new credit-normal type, so neither account ever goes below zero.
ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_accounts_type;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT ck_ledger_accounts_type CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'EXPENSE', 'REVENUE'));

INSERT INTO ledger_accounts (id, type, currency, balance_minor, system_code, created_at, version)
VALUES ('00000000-0000-0000-0000-00000000000d', 'EXPENSE', 'MXN', 0, 'CUSTOMER_ADJUSTMENTS', now(), 0),
       ('00000000-0000-0000-0000-00000000000e', 'EXPENSE', 'USD', 0, 'CUSTOMER_ADJUSTMENTS', now(), 0),
       ('00000000-0000-0000-0000-00000000000f', 'REVENUE', 'MXN', 0, 'ADJUSTMENT_RECOVERIES', now(), 0),
       ('00000000-0000-0000-0000-000000000010', 'REVENUE', 'USD', 0, 'ADJUSTMENT_RECOVERIES', now(), 0);

-- The support ticket an adjustment settles is only referenced: tickets belong to another module
CREATE TABLE adjustments (
    id               UUID         PRIMARY KEY,
    wallet_id        UUID         NOT NULL REFERENCES wallets (id),
    user_id          UUID         NOT NULL REFERENCES users (id),
    direction        VARCHAR(10)  NOT NULL,
    amount_minor     BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    reason           VARCHAR(300) NOT NULL,
    ticket_id        UUID,
    requested_by     UUID         NOT NULL REFERENCES users (id),
    requested_at     TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    decided_by       UUID         REFERENCES users (id),
    decided_at       TIMESTAMPTZ,
    rejection_reason VARCHAR(300),
    -- No foreign key, as in transfers: the ledger writes its entry when the transaction flushes, after this row
    journal_entry_id UUID,
    CONSTRAINT ck_adjustments_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_adjustments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_adjustments_status CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED')),
    -- Applied means money moved, and only then is there an entry
    CONSTRAINT ck_adjustments_applied_has_entry CHECK ((status = 'APPLIED') = (journal_entry_id IS NOT NULL)),
    CONSTRAINT ck_adjustments_rejected_has_reason CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_adjustments_decided CHECK ((status = 'PENDING_APPROVAL') = (decided_at IS NULL)),
    CONSTRAINT uk_adjustments_journal_entry UNIQUE (journal_entry_id)
);

CREATE INDEX ix_adjustments_pending ON adjustments (requested_at) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX ix_adjustments_ticket ON adjustments (ticket_id) WHERE ticket_id IS NOT NULL;
CREATE INDEX ix_adjustments_user ON adjustments (user_id, requested_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED',
    'DEPOSIT_RECEIVED', 'WITHDRAWAL_SENT', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'WALLET_FROZEN', 'WALLET_UNFROZEN',
    'PASSWORD_CHANGED', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED', 'DEPOSIT_REJECTED', 'WITHDRAWAL_FAILED',
    'BANK_ACCOUNT_ADDED', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'PHONE_CHANGED', 'ACCOUNT_UNLOCKED',
    'BANK_ACCOUNT_REMOVED', 'SESSIONS_REVOKED', 'SUPPORT_CASE_ASSIGNED', 'SUPPORT_AGENT_JOINED',
    'SUPPORT_AGENT_REPLIED', 'SUPPORT_CASE_RESOLVED', 'SUPPORT_TICKET_RESOLVED', 'WALLET_ADJUSTED'));
