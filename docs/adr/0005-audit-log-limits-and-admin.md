# 5. Audit log through an event outbox, daily limits and admin controls

- Status: accepted
- Date: 2026-10-30

## Context

Operations staff must be able to stop money moving out of a wallet under investigation, and later explain who did
what and when. Withdrawals open a new way for money to leave, so stolen credentials need a cap on the damage.
The audit trail must never miss an action, but writing it must never slow down or break the operation itself.

## Decision

- **Events with an outbox.** Modules publish small events (`DepositCompleted`, `WalletStatusChanged`,
  `UserLockedOut`...) carrying ids and amounts only, no personal data. Spring Modulith stores each event in
  `event_publication` inside the publishing transaction. After commit, listeners run asynchronously in their own
  transaction; an event stays pending until its listener succeeds and is retried on restart. Finished publications
  are deleted.
- **Audit module.** It listens to those events and appends to `audit_events` (actor, action, target, details).
  Each entry's id is derived from the event, and inserts use `ON CONFLICT DO NOTHING`, so a redelivered event never
  creates a duplicate. The table is append-only, protected by a trigger and revoked privileges like the ledger.
- **Daily outgoing limit.** Withdrawals and transfers sent count against a per-currency limit per wallet and local
  day (`America/Mexico_City`). `Wallets.reserveOutgoing` locks the wallet row, checks status and limit, and adds to
  `wallet_daily_outgoing` in the caller's transaction, so concurrent requests cannot both pass and a failed
  operation gives its amount back. Only the debited wallet is locked, so opposite transfers cannot deadlock.
- **Freeze.** Admins freeze or unfreeze a wallet with a mandatory reason. A frozen wallet cannot deposit, withdraw,
  send or receive, but its owner still sees it. Freezing takes the same row lock, so it waits for operations in
  flight and blocks the ones after it.
- **Administrator account.** Created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD`. An existing regular user
  with that email is never promoted, because without email verification anyone could have registered it first.
  Every `/api/v1/admin/**` endpoint requires `ROLE_ADMIN` through one rule in the security configuration.

## Consequences

- Audit entries appear shortly after the operation (asynchronously), but are never lost.
- The same outbox will feed notifications in phase 6, and could later feed a message broker.
- The usage table keeps one row per wallet and day; old rows can be purged by the phase 5 jobs.
- Limits are global per currency; per-user limits (e.g. by verification level) would need a new table.

## Update (2026-10-30)

Locking only the debited wallet let a freeze race with money coming in: a deposit or transfer could read the
destination as active, then credit it right after the freeze committed. Incoming money now takes a shared lock
(`SELECT ... FOR SHARE`) on the destination wallet, which a freeze has to wait for and which waits for a freeze.
Transfers lock both wallets in id order, like the ledger does with accounts, so opposite transfers still cannot
deadlock. Incoming operations do not block each other.
