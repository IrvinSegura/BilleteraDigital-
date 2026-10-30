# 4. Idempotency keys for money-moving requests

- Status: accepted
- Date: 2026-10-30

## Context

Mobile networks drop responses. A client that times out on `POST /transfers` cannot know whether the money moved,
so it retries; without protection the retry sends the money twice. Retries can also arrive while the first request
is still running.

## Decision

- `POST /api/v1/transfers` and `POST /api/v1/wallets/{id}/deposits` require an `Idempotency-Key` header
  (1 to 100 letters, digits, `-` or `_`; a UUID per user action is the expected value). Keys are scoped per user.
- The key is stored in `idempotency_keys` **in the same transaction** as the transfer or deposit, and it is
  inserted **before** any money moves, together with a SHA-256 fingerprint of the request and the id the new
  resource will get.
- Outcomes:
  - **First request**: runs normally; `201` with `Idempotent-Replayed: false`.
  - **Retry after success**: nothing runs; the stored resource is returned with the same status and body, plus
    `Idempotent-Replayed: true`.
  - **Concurrent retry**: its insert waits on the primary key until the first transaction finishes. If that one
    committed, the insert fails and the request replays the result; if it rolled back, the retry proceeds.
  - **Same key, different request** (other amount, recipient...): `422 IDEMPOTENCY_KEY_REUSED`.
  - **Failed request** (e.g. insufficient funds): the key rolls back with it, so the client can retry the same key
    once the problem is fixed. Errors are never cached.
- Amounts are compared by value (`10`, `10.0` and `10.00` are the same request) and emails case-insensitively.
- Transfers are addressed by recipient email. Unknown users and users without a wallet in the sender's currency get
  the same `RECIPIENT_NOT_FOUND`, to reveal as little as possible about which emails are registered.

## Consequences

- At most one money movement per user and key, enforced by the database rather than by application locks or a
  cache that could be lost.
- A replay returns the resource as stored (e.g. the balance right after the deposit), not the current state.
- Keys are kept indefinitely for now; a cleanup job with a retention period (e.g. 24 hours) comes with the phase 5
  jobs.
- Looking up recipients by email still lets a determined caller test whether an email is registered; rate limiting
  (phase 5) bounds how fast that can be done.
