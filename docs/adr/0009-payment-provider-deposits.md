# 9. Deposits paid through a payment provider

- Status: accepted
- Date: 2026-11-01

## Context

Deposits were simulated: the request itself credited the wallet (ADR 0003). With a real payment provider the money
is confirmed later, once the payer completes a checkout page, and the confirmation arrives as a webhook that may come
late, twice, out of order or never. A wallet must not show money the provider has not collected, nor lose money it did
collect, and the provider must never be called while the database holds locks.

## Decision

- **A `payments` module** owns the provider: the `payments` table, the webhook endpoint and the provider adapters. Its
  API is `Payments` (`register`, `start`, `refund`) plus the `PaymentSucceeded` and `PaymentFailed` events. Transfers
  depends on it; it depends on nothing but `shared`, so it can later serve other operations.
- **Deposits are asynchronous.** The request creates a `PENDING` deposit and registers its payment in one transaction
  (both share the deposit's id). After the commit, outside any transaction, the payment is started; the provider's
  checkout URL is returned with `202`. The ledger entry is written only when `PaymentSucceeded` arrives, under the
  deposit's row lock, so the request and the listener cannot both credit it. Deposits end `COMPLETED`, `EXPIRED`,
  `FAILED` (the provider could not be reached; `503`) or `REJECTED`.
- **Providers are pluggable** with `wallet.payments.provider`. `simulated` pays every checkout when it opens, so a
  deposit completes within the request (`201`, as before) and tests need no account. `stripe` uses Stripe Checkout in
  card-only mode: cards are confirmed while the payer waits, so a completed checkout is always paid; vouchers and bank
  transfers (OXXO, SPEI) would add a state that lasts days.
- **No Stripe SDK.** Four form-encoded calls go through Spring's `RestClient` with timeouts and an `Idempotency-Key`
  per payment, so a retried call never opens a second checkout or refunds twice. The webhook signature
  (HMAC-SHA256 over `timestamp.body`, five-minute tolerance, constant-time comparison) is checked by our own
  `StripeSignatureVerifier`, tested against a known vector. No API version is pinned; only stable fields are read.
- **Webhooks are idempotent.** Each event id is inserted into `payment_webhook_events` in the same transaction as its
  effect: a redelivery is skipped, and a failure rolls both back so Stripe's own retry processes it again. The
  endpoint needs no token; its path is public and rate-limited per address.
- **Lost webhooks are recovered by asking.** A job checks payments whose checkout expired more than ten minutes ago:
  paid ones succeed, open ones are expired at Stripe first (a payer may pay at that very moment), so every payment
  ends in one state. Payments registered but never started (the instance stopped in between) fail after ten minutes.
- **Money that arrives for a wallet that cannot take it is refunded.** When the payment succeeds, the account and
  wallet are checked again under their locks. If either refuses, crediting is rolled back and, in a new transaction,
  the deposit is rejected and a refund requested. The refund call is made by an outbox listener, so it is retried
  until Stripe answers. A payment that succeeds after its deposit expired or failed is refunded the same way.
- **The ledger still debits `SETTLEMENT`.** Money collected by Stripe is not in the bank until Stripe pays it out,
  minus fees. A separate clearing account would be exact, but every withdrawal credits `SETTLEMENT`, which cannot go
  negative, so withdrawals would fail until Stripe's payouts and fees are recorded too. Both are left for later.

## Consequences

- A client opens `checkoutUrl`, Stripe sends the payer back to `wallet.payments.return-url` with the deposit id, and
  the client polls `GET /api/v1/wallets/{id}/deposits/{depositId}` until it is no longer pending.
- The account is checked twice: when the deposit is requested, so nobody is sent to pay for money that would be
  refused, and when the money arrives.
- A replayed `Idempotency-Key` returns the deposit as it is now; a key whose deposit failed keeps returning it, so a
  new attempt needs a new key.
- Each deposit adds a payment row, outbox events and, with Stripe, at least one webhook. Webhook ids are kept 30 days,
  well past Stripe's three days of redeliveries.
- Stripe fees, payouts to the bank and reconciliation against Stripe's reports are not recorded. Withdrawals are
  still simulated and synchronous; paying out to a user's bank needs Stripe Connect or a SPEI provider.
