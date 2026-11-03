-- Money collected by a payment provider no longer lands straight in SETTLEMENT: it waits in PROVIDER_CLEARING until
-- the provider pays it out, minus its fees (PROVIDER_FEES, an expense). UNAPPLIED_PAYMENTS holds collected money no
-- wallet has received yet, and PLATFORM_CAPITAL (equity) is what the owners put into SETTLEMENT to fund payouts.
ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_accounts_type;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT ck_ledger_accounts_type CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'EXPENSE'));

INSERT INTO ledger_accounts (id, type, currency, balance_minor, system_code, created_at, version)
VALUES ('00000000-0000-0000-0000-000000000005', 'ASSET', 'MXN', 0, 'PROVIDER_CLEARING', now(), 0),
       ('00000000-0000-0000-0000-000000000006', 'ASSET', 'USD', 0, 'PROVIDER_CLEARING', now(), 0),
       ('00000000-0000-0000-0000-000000000007', 'LIABILITY', 'MXN', 0, 'UNAPPLIED_PAYMENTS', now(), 0),
       ('00000000-0000-0000-0000-000000000008', 'LIABILITY', 'USD', 0, 'UNAPPLIED_PAYMENTS', now(), 0),
       ('00000000-0000-0000-0000-000000000009', 'EXPENSE', 'MXN', 0, 'PROVIDER_FEES', now(), 0),
       ('00000000-0000-0000-0000-00000000000a', 'EXPENSE', 'USD', 0, 'PROVIDER_FEES', now(), 0),
       ('00000000-0000-0000-0000-00000000000b', 'EQUITY', 'MXN', 0, 'PLATFORM_CAPITAL', now(), 0),
       ('00000000-0000-0000-0000-00000000000c', 'EQUITY', 'USD', 0, 'PLATFORM_CAPITAL', now(), 0);

-- A capital contribution is recorded once per bank reference, so an administrator retrying the request cannot put
-- the same money in twice
CREATE UNIQUE INDEX uk_journal_entries_capital_reference ON journal_entries (reference)
    WHERE type = 'CAPITAL_CONTRIBUTION';
