# Digital Wallet

Digital wallet REST API with a double-entry ledger, built as a modular monolith with clean architecture.

**Stack:** Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway, Spring Modulith, ArchUnit, Testcontainers, Docker.

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
| 1 | Identity: registration, login, JWT (RS256), refresh token rotation, lockout | Pending |
| 2 | Accounts and ledger: wallets, system accounts, journal entries, deposits | Pending |
| 3 | Transfers: ordered pessimistic locking, idempotency keys, concurrency tests | Pending |
| 4 | Withdrawals, daily limits, account freeze, audit log, admin endpoints | Pending |
| 5 | Reconciliation job, rate limiting, metrics, tracing, dashboards | Pending |
| 6 | Event-driven notifications, load testing, documentation | Pending |
