# 6. Ledger reconciliation, rate limiting and observability

- Status: accepted
- Date: 2026-10-30

## Context

The ledger caches each account's balance next to its append-only postings (ADR 0003). Nothing so far proves the
two still agree, and a mismatch caused by a bug or a manual database edit would go unnoticed until a customer
complains. Login, registration and transfers by email can be called as fast as a client wants, which makes
credential stuffing and email enumeration cheap (ADR 0002, ADR 0004). Earlier phases also left tables that only
grow: expired refresh tokens, idempotency keys and daily usage rows. Finally, nobody can see what the service is
doing: there are logs, but no metrics, traces or alerts.

## Decision

### Reconciliation job

- An hourly job (`wallet.ledger.reconciliation.cron`) runs three checks over the whole ledger:
  1. **Account balance**: every cached balance equals the net of its postings on the account's normal side.
  2. **Balanced entries**: every journal entry has at least two postings, one currency, and debits equal to credits.
  3. **Trial balance**: per currency, assets (settlement accounts) equal liabilities (user wallets).
- All checks run in one **repeatable-read** transaction, so they see a single snapshot; transfers committing in the
  meantime are invisible to the run instead of showing up as false alarms, and nothing is locked for them.
- Only one instance reconciles at a time: the run first takes a transaction-scoped PostgreSQL advisory lock
  (`pg_try_advisory_xact_lock`). Other instances skip that run; a manual run gets `409 RECONCILIATION_IN_PROGRESS`.
  The lock disappears with the transaction, so a crash can never leave it held. No ShedLock table is needed.
- Every run and its discrepancies (at most 100 per check) are stored in append-only tables, protected like the
  ledger. Admins list them with `GET /api/v1/admin/reconciliations` and start one with `POST` on the same path.
- The job reports, it never repairs. Fixing the ledger means a reversing entry decided by a person.

### Cleanup jobs

| Data | Rule | Why it is safe |
|---|---|---|
| Refresh tokens | Delete a login family once all of its tokens have expired | A family with any live token is kept whole, so reuse of a rotated token is still detected |
| Idempotency keys | Delete after 24 hours | Clients retry within seconds or minutes; after 24 hours the same key is a new request, as in most payment APIs |
| Daily usage rows | Delete after 7 days | The limit only reads today's row; the audit log keeps the operations |

Deletes are idempotent, so these jobs run on every instance without a lock. Scheduling can be switched off with
`wallet.scheduling.enabled` (tests do so and call the services directly).

### Rate limiting

- A servlet filter placed right after the Spring Security filter chain applies token buckets (Bucket4j), keyed by
  rule and client. Rules are configuration (`wallet.rate-limit.rules`) and the first matching rule applies:

  | Rule | Requests | Key | Limit |
  |---|---|---|---|
  | `auth` | `POST /api/v1/auth/**` | client address | 20 per minute |
  | `money-movement` | `POST` deposits, withdrawals and transfers | user | 30 per minute |
  | `api` | anything else under `/api/` | user | 300 per minute |

- Rejections answer `429 RATE_LIMITED` in the usual Problem Details format, with `Retry-After`. Every limited
  response carries `X-RateLimit-Remaining`.
- Running after authentication lets buckets follow the user rather than the address (people behind one office
  network do not share a limit), and requests the security chain already rejected cost nothing.
- The client address is `request.getRemoteAddr()`. Behind a load balancer the deployment sets
  `SERVER_FORWARD_HEADERS_STRATEGY=native`; `X-Forwarded-For` is never read directly because clients can forge it.
- Buckets live in memory, in a Caffeine cache bounded to 100,000 clients and expiring idle buckets.

### Metrics, tracing and dashboards

- **Metrics**: Micrometer with a Prometheus endpoint. Besides HTTP (with histograms for percentiles), JVM and
  connection pool metrics, the application publishes:
  - `wallet_money_moved` (count and sum per type and currency), recorded after commit so rolled-back operations are
    never counted.
  - `wallet_business_rejections_total` per error code, `wallet_rate_limit_rejections_total` per rule.
  - `wallet_ledger_reconciliation_discrepancies` and `wallet_ledger_reconciliation_last_run_seconds`, read from the
    latest stored run, so every instance reports the same value whichever one ran the job.
  - `wallet_events_pending` and `wallet_events_oldest_pending_age_seconds` for the event outbox (ADR 0005).
- **Tracing**: Micrometer Tracing with the OpenTelemetry bridge, exported over OTLP. Spans cover HTTP requests,
  scheduled jobs and every SQL statement and connection checkout (datasource-micrometer, without parameter values).
  Async event listeners continue the trace of the request that published the event. Each request span carries the
  `correlation.id` returned to the client, and log lines include `traceId` and `spanId`. Sampling is 100% in
  development and 10% by default in production.
- **Actuator exposure**: in development and production actuator runs on a separate management port (8081) that is
  never published outside the private network, so Prometheus and health probes need no credentials there. When
  actuator shares the API port (tests), everything except health and info requires the `ADMIN` role.
- **Local stack**: `docker compose --profile observability up -d` starts Prometheus (with alert rules), Tempo and
  Grafana with a provisioned "Digital Wallet" dashboard. Latency panels show exemplars that open the matching trace.
- **Alerts** (Prometheus rules): any ledger discrepancy, no reconciliation for two hours, an outbox event waiting
  over five minutes, more than 5% server errors, p95 latency above one second, and sustained rate-limit rejections.

## Alternatives considered

- **Reconciling incrementally from a checkpoint** scales to huge ledgers but needs a way to detect edits to old
  rows; a full pass is simple and fast at this size, and the snapshot keeps it correct under load.
- **Rate limits in PostgreSQL or Redis** would be shared across instances, at the cost of a round trip on every
  request (or a new piece of infrastructure). Per-instance limits are enough to stop abuse; a shared store
  (Bucket4j has adapters for both) can replace the cache without changing the filter.
- **Spring Modulith observability** adds a span per call into each module, but it wraps every exposed bean in a
  CGLIB proxy; filters and exception handlers with final methods then run against an uninitialized proxy and fail.
  SQL spans plus HTTP spans already show where time goes, so only the Modulith actuator endpoint is used.

## Consequences

- A bug that breaks double entry, or a manual edit of a balance, is reported within an hour and raises an alert.
- Reconciliation reads every posting each hour; at millions of postings it will need the incremental approach.
- With N instances a client can get up to N times the configured rate.
- The service exposes enough signals to set SLOs (latency, error rate) and to debug a slow transfer down to the
  SQL statement that waited for a lock.
