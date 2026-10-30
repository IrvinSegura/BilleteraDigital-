# Digital Wallet

Digital wallet REST API with a double-entry ledger, built as a modular monolith with clean architecture.

**Stack:** Java 21, Spring Boot 4.1, Spring Security (OAuth2 resource server, JWT RS256), PostgreSQL 17, Flyway,
Spring Modulith, ArchUnit, Testcontainers, Docker, Micrometer (Prometheus, OpenTelemetry tracing), Bucket4j,
Grafana and Tempo.

## Architecture

```
com.irvinsegura.wallet
├── shared          open kernel: Money, DomainException, error handling, correlation id, rate limiting
├── identity        users, authentication, tokens
├── accounts        wallets, status, limits
├── ledger          double-entry journal, balances, reconciliation
├── transfers       deposits, withdrawals, transfers (idempotent)
├── audit           append-only audit log (event listener)
└── notifications   user notifications (event listener)
```

Each module follows the same layout, and dependencies always point inward:

```
web ──> application ──> domain <── infrastructure
```

- `domain` has no framework dependencies.
- Module boundaries are verified by `ModularityTests` (Spring Modulith) and layer rules by
  `CleanArchitectureTests` (ArchUnit). Breaking either fails the build.
- Decisions are recorded in [docs/adr](docs/adr).

## Running locally

Requirements: JDK 21 and Docker.

```bash
cp .env.example .env
docker compose up -d
./mvnw spring-boot:run
```

- PostgreSQL listens on host port 5433 (`DB_PORT`) to avoid clashing with other local databases.
- API: http://localhost:8080. Actuator has its own port, as in production:
  http://localhost:8081/actuator/health and http://localhost:8081/actuator/prometheus
- API docs (dev profile only): http://localhost:8080/swagger-ui.html

### Database users

| User | Used by | Privileges |
|---|---|---|
| `wallet_owner` | Flyway | Owns the schema, runs migrations |
| `wallet_app` | Application | Data access only; ledger and audit tables will be append-only (no UPDATE/DELETE) |

The app user is created by `docker/postgres/init` the first time the volume is initialized.
To reset the database: `docker compose down -v`.

### Running everything in containers

```bash
docker compose --profile app up -d --build
```

### Troubleshooting on Windows

If startup fails with `Unable to establish loopback connection`, the JDK cannot create its internal socket in
`%TEMP%` (this happens when the Windows user name contains spaces and `%TEMP%` is a short `~1` path).
Add this VM option to the run configuration, pointing to any existing folder with a short path:

```
-Djdk.net.unixdomain.tmpdir=C:\tmp
```

## Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Create an account (`201`, `409` if the email exists) |
| POST | `/api/v1/auth/login` | public | Get an access token (15 min) and a refresh token (7 days) |
| POST | `/api/v1/auth/refresh` | public | Swap a refresh token for a new pair; the old one stops working |
| POST | `/api/v1/auth/logout` | public | Revoke the refresh token and every token rotated from it |
| GET | `/api/v1/users/me` | bearer | Profile of the authenticated user |

Every other endpoint requires `Authorization: Bearer <accessToken>`. Five failed logins in a row lock the account
for 15 minutes. Design and trade-offs: [ADR 0002](docs/adr/0002-jwt-access-and-rotating-refresh-tokens.md).

### Signing keys

The dev and test profiles generate a temporary RSA key pair at startup, so restarting the app invalidates all
tokens. Production requires a fixed pair:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

Mount both files as secrets and point `JWT_PRIVATE_KEY_LOCATION` and `JWT_PUBLIC_KEY_LOCATION` at them
(e.g. `file:/run/secrets/jwt-private.pem`). `*.pem` files are git-ignored.

## Wallets and deposits

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/wallets` | Open a wallet (`{"currency": "MXN"}`); one per currency, MXN and USD supported |
| GET | `/api/v1/wallets` | Your wallets with their balances |
| GET | `/api/v1/wallets/{id}` | One wallet; other users' wallets answer `404` |
| GET | `/api/v1/wallets/{id}/movements?page=0&size=20` | Statement, newest first, with the balance after each movement |
| POST | `/api/v1/wallets/{id}/deposits` | Simulated top-up (`{"amount": 100.50}`); needs `Idempotency-Key` |
| POST | `/api/v1/wallets/{id}/withdrawals` | Simulated payout to the user's bank; needs `Idempotency-Key` |

All of them require a bearer token. Amounts are returned as decimal strings (`{"amount": "100.50", "currency":
"MXN"}`) so clients never lose cents to floating point. Money is recorded in a double-entry ledger: see
[ADR 0003](docs/adr/0003-double-entry-ledger.md).

## Transfers

```http
POST /api/v1/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: 6f1c2a9e-3b7d-4c55-9a0e-2f8b1d4c7e10

{"sourceWalletId": "...", "recipientEmail": "bob@example.com", "amount": 30.25, "description": "Dinner"}
```

The recipient receives into their wallet in the sender's currency. `GET /api/v1/transfers/{id}` returns a transfer
to its sender, and both statements show it with the transfer id as `operationId`.

Every request that moves money needs an `Idempotency-Key` (use a new UUID per user action):

| Situation | Response |
|---|---|
| First request | `201`, `Idempotent-Replayed: false` |
| Retry with the same key and body, even concurrent | Same `201` body, `Idempotent-Replayed: true`; money moves once |
| Same key, different body | `422 IDEMPOTENCY_KEY_REUSED` |
| Request failed (e.g. `INSUFFICIENT_FUNDS`) | Nothing is stored; the key can be retried |

Details in [ADR 0004](docs/adr/0004-idempotency-keys.md).

## Limits and administration

- **Daily limit:** withdrawals and transfers sent share a limit per wallet and local day (Mexico City time):
  50,000.00 MXN and 3,000.00 USD by default (`wallet.accounts.daily-outgoing-limit`). Going over it returns
  `422 DAILY_LIMIT_EXCEEDED` with what is left for today. Deposits are not limited.
- **Administrator:** created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD` (dev profile:
  `admin@wallet.local` / `admin-dev-password`). An existing user with that email is never promoted.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/wallets/{id}` | Any wallet with owner, balance and status |
| POST | `/api/v1/admin/wallets/{id}/freeze` | Block all money movement (`{"reason": "..."}`, required) |
| POST | `/api/v1/admin/wallets/{id}/unfreeze` | Allow it again (`{"reason": "..."}`) |
| GET | `/api/v1/admin/audit-events?targetId=&actorId=&page=&size=` | Audit log, newest first |

Every `/api/v1/admin/**` endpoint requires the `ADMIN` role (`403 FORBIDDEN` otherwise). Registrations, lockouts,
wallet changes and every money movement are written to an append-only audit log through Spring Modulith's event
outbox, so no entry is lost even if the app crashes right after an operation. See
[ADR 0005](docs/adr/0005-audit-log-limits-and-admin.md).

## Operations

### Ledger reconciliation

Every hour a job checks that each cached balance equals the sum of its postings, that every journal entry balances,
and that per currency the platform's assets equal what it owes users. It reads one consistent snapshot, so it never
blocks or misreads transfers in flight, and only one instance runs it at a time.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/reconciliations?limit=20` | Latest runs, newest first, with any discrepancies |
| POST | `/api/v1/admin/reconciliations` | Run one now (`409 RECONCILIATION_IN_PROGRESS` if one is running) |

Nightly jobs also delete expired refresh token families, idempotency keys older than 24 hours and daily usage rows
older than 7 days.

### Rate limits

| Requests | Limit | Counted per |
|---|---|---|
| `POST /api/v1/auth/**` | 20 per minute | client address |
| `POST` deposits, withdrawals, transfers | 30 per minute | user |
| Any other `/api/**` request | 300 per minute | user |

Over the limit the API answers `429 RATE_LIMITED` with `Retry-After` (seconds); limited responses include
`X-RateLimit-Remaining`. Limits are configured in `wallet.rate-limit` and apply per instance. Behind a load balancer
set `SERVER_FORWARD_HEADERS_STRATEGY=native` so limits see the client's address.

### Metrics, traces and dashboards

```bash
docker compose --profile observability up -d
```

| Tool | URL | What it shows |
|---|---|---|
| Grafana | http://localhost:3000 | "Digital Wallet" dashboard: traffic, latency, money moved, rejections, ledger and outbox health, JVM and database pool (sign in as `admin`/`admin` to edit) |
| Prometheus | http://localhost:9090 | Raw metrics and alert rules (`docker/observability/prometheus/alerts.yml`) |
| Tempo | through Grafana, Explore > Tempo | Traces of each request down to every SQL statement |

The app started from the IDE sends traces to Tempo on `localhost:4318` and is scraped on port 8081; while the stack
is not running, trace exports only log warnings. Every response's `X-Correlation-Id` is also on its trace
(`correlation.id`), and log lines show `[correlationId,traceId,spanId]`. In latency panels, dots are exemplars:
click one to open that request's trace. Design and trade-offs:
[ADR 0006](docs/adr/0006-reconciliation-rate-limiting-and-observability.md).

## Tests

```bash
./mvnw verify
```

Integration tests start a real PostgreSQL with Testcontainers, so Docker must be running.

## Error format

Every error follows RFC 9457 Problem Details plus a stable `code` and the request's `correlationId`:

```json
{
  "type": "about:blank",
  "title": "Business rule violation",
  "status": 422,
  "detail": "Not enough balance",
  "code": "INSUFFICIENT_FUNDS",
  "correlationId": "5f0c..."
}
```


Send an `X-Correlation-Id` header to trace a request across logs; one is generated otherwise.

