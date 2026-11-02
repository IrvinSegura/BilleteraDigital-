# 10. Asynchronous withdrawals with money in transit

- Status: accepted
- Date: 2026-11-01

## Context

Withdrawals were simulated and final at once: one entry moved the money from the wallet to the settlement account
(ADR 0003). A real payout (SPEI, or a provider such as Stripe Connect) is confirmed minutes or hours later, and may be
refused, e.g. for a closed account. Until then the money must not be spendable twice, must still be owed to someone
in the ledger, and must be able to come back.

## Decision

- **Money in transit is a liability.** Each currency gets a `PAYOUTS_IN_TRANSIT` system account. Requesting a
  withdrawal moves the money from the wallet into it (`WITHDRAWAL`), so the balance drops at once and the trial
  balance still holds: the platform owes the money until the bank confirms. A confirmed payout moves it from transit
  to `SETTLEMENT` (`WITHDRAWAL_PAID`, which the wallet's statement never shows); a refused one moves it back to the
  wallet (`WITHDRAWAL_REVERSAL`).
- **Withdrawals have a status:** `PENDING`, `PAID` or `FAILED`, with the provider's reference, the second entry and
  the failure reason. `WithdrawalCompleted` is now published when the payout is paid, and `WithdrawalFailed` when it
  is refused; both feed notifications and the audit log.
- **The provider is called after the commit** that took the money, never while locks are held, through a
  `PayoutGateway` port that must be idempotent per withdrawal. The request tries once: `201` if the payout is already
  paid, `202` while it is on its way. If the provider cannot be reached, the withdrawal simply stays pending.
- **A job moves pending withdrawals forward** every minute: it sends again the payouts the provider never accepted and
  asks about the ones on their way. It skips withdrawals younger than a minute, which the request is still sending.
  Every answer is applied under the withdrawal's row lock, so the request and the job, or two instances, settle each
  withdrawal once.
- **A refused payout returns the money even to a frozen wallet or a disabled account**: it is the user's own money
  coming back, not new money arriving, so the incoming-money check of deposits does not apply.
- **MXN payouts go to a CLABE.** Users register bank accounts (`/api/v1/bank-accounts`, check digit validated, at
  most five, full number never returned) and every MXN withdrawal names one; USD withdrawals take none. Adding one is
  notified and audited, since it is where a taken-over account would send money. The provider is still simulated; a
  real SPEI participant (e.g. STP) only needs a new `PayoutGateway` adapter.

## Consequences

- Clients follow a pending withdrawal with `GET /api/v1/wallets/{id}/withdrawals/{withdrawalId}`.
- A refused payout gives its amount back to the outgoing limit of the day it was requested.
- The reconciliation sees money in transit as an ordinary liability, so a payout stuck in `PENDING` is not a ledger
  discrepancy. It shows in `wallet_withdrawals_oldest_pending_age_seconds` instead, and the `PayoutStuck` alert fires
  after 30 minutes; refused payouts are counted by reason in `wallet_withdrawals_failed_total`.
- Adding a bank account takes a per-owner advisory lock, so concurrent requests cannot go past the limit of five.
- Withdrawals made before this change are `PAID` without a second entry, since a single entry paid them out.
