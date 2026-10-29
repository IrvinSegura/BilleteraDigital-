# 1. Modular monolith with clean architecture per module

- Status: accepted
- Date: 2026-10-29

## Context

Moving money requires strong consistency: a transfer must debit and credit atomically. A microservices
split would force distributed transactions (sagas, compensations) before the domain itself is proven,
adding operational cost with no benefit at this stage.

## Decision

- One deployable Spring Boot application and one PostgreSQL database.
- Business capabilities are top-level packages (`identity`, `accounts`, `ledger`, `transfers`, `audit`,
  `notifications`) verified as Spring Modulith application modules. `shared` is an open kernel.
- Inside each module: `domain` (framework-free), `application` (use cases), `infrastructure` (adapters),
  `web` (controllers and DTOs). Dependencies point inward; ArchUnit fails the build otherwise.
- Calls that must share a transaction (transfers -> ledger) are synchronous through a port.
  Side effects (audit, notifications) react to events persisted with the transaction (outbox).

## Consequences

- Strong consistency through plain database transactions.
- Module boundaries are enforced by tests, so a module can later be extracted into a service by
  replacing in-process events with a broker.
- More mapping code (domain <-> JPA entities) in exchange for a domain that is testable without a database.
