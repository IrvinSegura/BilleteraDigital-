# Digital Wallet

Digital wallet REST API with a double-entry ledger, built as a modular monolith with clean architecture.

**Stack:** Java 21, Spring Boot 4.1, Spring Security (OAuth2 resource server, JWT RS256), PostgreSQL 17, Flyway,
Spring Modulith, ArchUnit, Testcontainers, Docker.

## Architecture

```
com.irvinsegura.wallet
├── shared          open kernel: Money, DomainException, error handling, correlation id
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
- Health: http://localhost:8080/actuator/health
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
| POST | `/api/v1/wallets/{id}/deposits` | Simulated top-up (`{"amount": 100.50}`) |

All of them require a bearer token. Amounts are returned as decimal strings (`{"amount": "100.50", "currency":
"MXN"}`) so clients never lose cents to floating point. Money is recorded in a double-entry ledger: see
[ADR 0003](docs/adr/0003-double-entry-ledger.md).

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

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Project skeleton, modules, architecture tests, Docker, CI | Done |
| 1 | Identity: registration, login, JWT (RS256), refresh token rotation, lockout | Done |
| 2 | Accounts and ledger: wallets, system accounts, journal entries, deposits | Done |
| 3 | Transfers: ordered pessimistic locking, idempotency keys, concurrency tests | Pending |
| 4 | Withdrawals, daily limits, account freeze, audit log, admin endpoints | Pending |
| 5 | Reconciliation job, rate limiting, metrics, tracing, dashboards | Pending |
| 6 | Event-driven notifications, load testing, documentation | Pending |
