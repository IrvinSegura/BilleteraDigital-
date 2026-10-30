# 7. Notifications through the outbox and retries of failed events

- Status: accepted
- Date: 2026-10-30

## Context

Users need to learn that money arrived, left or was blocked without polling their statements, and an account
lockout should reach the owner, who may be the victim of password guessing. Every action already publishes an
event through the outbox (ADR 0005), so notifications can follow the same path as the audit log. Email adds a
dependency that fails on its own schedule: a mail server outage must not delay the in-app inbox, lose emails or
block money movements. Until now a failed listener was only retried when the application restarted.

## Decision

- **Inbox.** The notifications module listens to `UserRegistered`, `UserLockedOut`, `DepositCompleted`,
  `WithdrawalCompleted`, `TransferCompleted` (one notification per side) and `WalletStatusChanged`, and stores
  rows in `notifications`. The text is written at that moment, so it never changes afterwards. A freeze reason is
  an internal note and is never shown to the user.
- **Exactly one notification per event and recipient.** The id is derived from the type, the event and the
  recipient, and inserts use `ON CONFLICT DO NOTHING`, as in the audit log.
- **Email as a second outbox step.** Storing a new notification publishes an internal `NotificationCreated` event
  in the same transaction; a separate listener sends the email. The inbox commits even if the mail server is down,
  and a notification that already existed (a redelivered event) never sends a second email. Delivery is at least
  once: a crash after the server accepted the email but before the publication completed sends it again.
- **No personal data in events.** Events still carry ids only. The email listener looks up the address and name
  through `UserDirectory.findContact` right before sending, and never logs them.
- **Delivery is configuration.** `wallet.notifications.email.delivery` is `smtp` (Spring Mail, `spring.mail.*`) or
  `log` (writes the subject only). Choosing `smtp` without a host fails at startup. Locally and in tests, Mailpit
  receives the emails. JavaMail timeouts are set so a hung server fails the listener instead of blocking it, and
  the mail health indicator is off so an outage does not mark the service down.
- **API.** `GET /api/v1/notifications` (newest first, `unreadOnly`), `GET /unread-count`, `POST /{id}/read`
  (idempotent, keeps the first read time) and `POST /read-all`. Queries are always scoped to the token's user;
  someone else's id answers `404 NOTIFICATION_NOT_FOUND`.
- **Retention.** A nightly job deletes notifications older than 90 days, read or not. Statements and the audit log
  remain the record of what happened.
- **Retrying failed events.** A job every 30 seconds resubmits failed publications through Spring Modulith's
  `FailedEventPublications`. Each publication waits twice as long as before (1 minute, 2, 4... up to 1 hour) since
  its last attempt. Resubmission claims each publication with a conditional update, so instances running the job
  at the same time never retry the same one twice. Publications stuck in one state for 10 minutes (an instance died
  mid-listener) are marked failed by Modulith's staleness monitor and retried the same way.

## Alternatives considered

- **Sending the email in the inbox listener** is simpler, but an outage would roll back the inbox row too and tie
  both to the slowest dependency.
- **A message broker** (Kafka, RabbitMQ) would add infrastructure for a single consumer. The outbox already gives
  durable, retried delivery; events can be externalized to a broker later without changing the listeners.
- **Push notifications** need a mobile client and a provider account; the email sender port can gain a push
  implementation later.

## Consequences

- Users see their notifications shortly after the operation, and emails recover by themselves after an outage,
  within an hour of the server coming back.
- A listener with a bug is retried forever, at most once an hour, and keeps the outbox alerts firing until fixed.
- Clients poll the unread count; real-time delivery (WebSocket or server-sent events) is not provided.
- Users cannot choose which notifications they receive by email yet; preferences would need a new table.
