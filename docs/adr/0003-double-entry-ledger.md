# 3. Double-entry ledger with cached, locked balances

- Status: accepted
- Date: 2026-10-29

## Context

Wallet balances must never be wrong: not under concurrent requests, not after a crash, and not after a bug in
some other module. Every movement also has to be explainable later, for statements, support and audits.

## Decision

- **Double entry.** Every movement is a journal entry with postings whose debits equal their credits, so money is
  never created or destroyed. Accounts are typed: `ASSET` (what the platform holds, e.g. the `SETTLEMENT` account at
  the partner bank) increases with debits; `LIABILITY` (what it owes, e.g. each user wallet) increases with credits.
  A deposit debits settlement and credits the wallet; a transfer debits one wallet and credits another.
- **Non-negative balances everywhere.** Balances are kept on each account's normal side and can never drop below
  zero, enforced by the domain and by `CHECK` constraints. The sum of assets always equals the sum of liabilities.
- **Cached balance, updated under a row lock.** `ledger_accounts.balance_minor` is updated in the same transaction
  as the postings, after locking the accounts with `SELECT ... FOR UPDATE ORDER BY id`. The fixed order means two
  entries touching the same accounts in opposite directions wait for each other instead of deadlocking. Each posting
  stores `balance_after`, so statements need no replay.
- **Append-only journal.** `journal_entries` and `postings` reject `UPDATE` and `DELETE` through a trigger, for every
  database user, and the application role also loses those privileges. Mistakes are fixed with reversing entries.
- **Module API.** Other modules only see the `Ledger` interface and its records in `com.irvinsegura.wallet.ledger`.
  Calls join the caller's transaction, so a deposit and its journal entry commit together. Supported currencies are
  the currencies that have a settlement account (MXN and USD today).
- **No foreign keys across modules.** `wallets` references users and ledger accounts by id only, so each module keeps
  ownership of its tables and could be extracted later.
- **Unlocked reads use projections.** Reading an account before locking it in the same transaction would otherwise
  leave a stale entity in Hibernate's persistence context and fail when the lock is taken.

## Consequences

- Balances are read in O(1) and are always consistent with the postings written in the same transaction.
- Every deposit locks the currency's settlement account, so deposits in one currency are serialized. That is fine
  at this scale; if it becomes a bottleneck, the settlement account can be split into several shards.
- Deposits are simulated (no payment provider) and not yet idempotent: a retried request deposits twice. Idempotency
  keys arrive with transfers in phase 3.
- A reconciliation job comparing cached balances with the sum of postings is planned for phase 5.
