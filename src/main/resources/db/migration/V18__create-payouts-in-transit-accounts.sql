-- Payouts in transit: money already taken from wallets for withdrawals the bank has not confirmed. A liability, like
-- wallet balances: the platform still owes it until the payout lands, so assets and liabilities keep matching.
INSERT INTO ledger_accounts (id, type, currency, balance_minor, system_code, created_at, version)
VALUES ('00000000-0000-0000-0000-000000000003', 'LIABILITY', 'MXN', 0, 'PAYOUTS_IN_TRANSIT', now(), 0),
       ('00000000-0000-0000-0000-000000000004', 'LIABILITY', 'USD', 0, 'PAYOUTS_IN_TRANSIT', now(), 0);
