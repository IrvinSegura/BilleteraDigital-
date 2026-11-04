# Digital Wallet

Digital wallet REST API with a double-entry ledger, built as a modular monolith with clean architecture.

**Stack:** Java 25, Spring Boot 4.1, Spring Security (OAuth2 resource server, JWT RS256), PostgreSQL 17, Flyway,
Spring Modulith, ArchUnit, Testcontainers, Docker, Micrometer (Prometheus, OpenTelemetry tracing), Bucket4j,
Grafana, Tempo, Spring Mail and Mailpit, Stripe Checkout.

## Architecture

```
com.irvinsegura.wallet
├── shared          open kernel: Money, DomainException, error handling, correlation id, rate limiting
├── identity        users, authentication, tokens
├── accounts        wallets, status, limits
├── ledger          double-entry journal, balances, reconciliation
├── transfers       deposits, withdrawals, transfers (idempotent)
├── payments        payment provider (Stripe or simulated), checkout, webhooks, refunds
├── audit           append-only audit log (event listener)
└── notifications   in-app inbox and emails (event listener)
```

Each module follows the same layout, and dependencies always point inward:

```
web ──> application ──> domain <── infrastructure
```

- `domain` has no framework dependencies.
- Module boundaries are verified by `ModularityTests` (Spring Modulith) and layer rules by
  `CleanArchitectureTests` (ArchUnit). Breaking either fails the build.

## Running locally

Requirements: JDK 25 and Docker.

```bash
cp .env.example .env   # then fill in MAIL_USERNAME, MAIL_PASSWORD and EMAIL_FROM
docker compose up -d
./mvnw spring-boot:run
```

- PostgreSQL listens on host port 5433 (`DB_PORT`) to avoid clashing with other local databases.
- Emails are real, sent through Gmail as in production, so use addresses you can read. The dev profile reads `.env`
  itself, so runs from the IDE need no extra environment variables.
- API: http://localhost:8080. Actuator has its own port, as in production:
  http://localhost:8081/actuator/health and http://localhost:8081/actuator/prometheus
- API docs (dev profile only): http://localhost:8080/swagger-ui.html

### Database users

| User | Used by | Privileges |
|---|---|---|
| `wallet_owner` | Flyway | Owns the schema, runs migrations |
| `wallet_app` | Application | Data access only; ledger, reconciliation and audit tables are append-only (no UPDATE/DELETE) |

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
| POST | `/api/v1/auth/register` | public | Sign up (`email`, `password`, `fullName`, `captchaToken`); always `202`, whether or not the email has an account |
| POST | `/api/v1/auth/login` | public | Get an access token (15 min) and a refresh token (7 days); with two-factor on, also send `twoFactorCode`. `403 EMAIL_NOT_VERIFIED` until the email is verified; send `"resendVerification": true` to get a new link |
| POST | `/api/v1/auth/refresh` | public | Swap a refresh token for a new pair; the old one stops working |
| POST | `/api/v1/auth/logout` | public | Revoke the refresh token and every token rotated from it |
| POST | `/api/v1/auth/email-verification/confirm` | public | Confirm the email with the token from the emailed link (`204`) |
| POST | `/api/v1/auth/password-reset` | public | Email a reset link (`{"email", "captchaToken"}`); always `202`, registered or not |
| POST | `/api/v1/auth/password-reset/confirm` | public | Set a new password with the link's token; ends every session and lifts a lockout |
| GET | `/api/v1/users/me` | bearer | Profile of the authenticated user, with `phone`, `status`, `emailVerified` and `twoFactorEnabled` |
| PATCH | `/api/v1/users/me` | bearer | Change `fullName` (1 to 100 characters, spaces collapsed) and `phone` (E.164 like `+525512345678`; spaces, dashes and parentheses are dropped, `""` removes it); a field left out keeps its value. `422 INVALID_PROFILE` otherwise |
| GET | `/api/v1/users/me/sessions` | bearer | Signed-in devices: user agent, IP address, when it started and was last used, and which one is `current` |
| DELETE | `/api/v1/users/me/sessions/{id}` | bearer | End one session (`204`; `404 SESSION_NOT_FOUND` if it isn't yours or already ended) |
| DELETE | `/api/v1/users/me/sessions` | bearer | End every session but the current one (`{"revoked": 2}`) |
| POST | `/api/v1/users/me/email-verification` | bearer | Send a new verification link (`202`); older links stop working |
| POST | `/api/v1/users/me/password` | bearer | Change the password (`currentPassword`, `newPassword`); ends every other session and returns new tokens |
| POST | `/api/v1/users/me/two-factor` | bearer | Start setting up an authenticator app (`{"currentPassword"}`): returns `secret` and `otpauthUri` for a QR code (`201`) |
| POST | `/api/v1/users/me/two-factor/confirm` | bearer | Turn it on with a code from the app (`{"code"}`); returns 10 recovery codes, shown only once |
| POST | `/api/v1/users/me/two-factor/disable` | bearer | Turn it off (`{"currentPassword", "code"}`, an app code or a recovery code) (`204`) |
| GET | `/api/v1/users/me/two-factor` | bearer | Whether it is on and how many recovery codes are left |

Every other endpoint requires `Authorization: Bearer <accessToken>`. Five failed logins in a row lock the account
for 15 minutes.

Passwords (on sign-up, change and reset) need 12 to 64 characters and no particular mix (`422 WEAK_PASSWORD`
otherwise), following NIST SP 800-63B. Ones that are easy to guess are refused with `422 COMMON_PASSWORD`: common
passwords and words (`common-passwords.txt` next to `PasswordPolicy`), the same ones dressed up with digits, symbols,
accents or look-alike characters (`P@ssw0rd2024!`, `Contraseña123`), repeats, sequences and keyboard walks
(`123456789012`, `qwertyuiopas`) and the user's own name or email (`AnaLopez1990`). Passwords that pass those
rules are then looked up in Have I Been Pwned's Pwned Passwords and refused with `422 BREACHED_PASSWORD` when they
appear in a known breach. Only the first 5 characters of the password's SHA-1 are sent (k-anonymity) and responses are
padded. The lookup gives up after 2 seconds and lets the password through, so an outage never blocks sign-ups; it runs
before the database transaction. Turn it off with `PASSWORD_BREACH_CHECK=false` (tests do).

Ending a session revokes its refresh token at once, so it can't be renewed; an access token it already holds stays
valid until it expires (at most 15 minutes). Access tokens carry the session id as the `sid` claim, which is how
`current` is told apart.

### Web apps

A browser app should send `X-Session-Mode: cookie` on login, refresh, logout and password change. The refresh token
then travels only in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie scoped to `/api/v1/auth`, so page scripts
(and an XSS bug) never see it: the JSON answer leaves `refreshToken` null, and `POST /api/v1/auth/refresh` with that
header and no body uses the cookie. The cookie is ignored without the header, which a page on another site cannot
add, so forged cross-site requests cannot use it. Keep the access token in memory only. Mobile and server clients
keep sending and receiving the refresh token in the body, as before.

The API answers CORS only for the origins in `CORS_ALLOWED_ORIGINS` (comma-separated, exact origins, with
credentials); none by default. A dev server proxying `/api` to `localhost:8080` needs none. `SameSite=Strict` means
the app and the API must share a site, e.g. `app.example.com` and `api.example.com`.

### Two-factor authentication

Users can add an authenticator app (TOTP: 6 digits, 30-second steps, any app such as Google Authenticator or
1Password). Until the first code is confirmed nothing changes, so a setup abandoned halfway never locks anyone out.
Turning it on ends every session, the caller's too, so a session stolen before has no way to keep going; the next
login asks for the code. Once it is on:

- Login answers `403 TWO_FACTOR_REQUIRED` after a correct password without `twoFactorCode`; a wrong code is a failed
  login (`401`) and counts toward the lockout, which is what makes guessing six digits hopeless.
- Each code works once, so a code read over someone's shoulder is useless afterwards. A recovery code (`xxxxx-xxxxx`)
  works wherever an app code does, also once.
- Adding a bank account needs `twoFactorCode` too, and turning two-factor off needs the password and a code.
- Support can turn it off for someone who lost their phone and recovery codes
  (`POST /api/v1/admin/users/{id}/two-factor/reset`, with a reason); the user is notified and it is audited.
- Administrators must have it: until they turn it on and sign in again with a code, every `/api/v1/admin/**`
  endpoint answers `403 ADMIN_TWO_FACTOR_REQUIRED` (`wallet.identity.admin-requires-two-factor`, off only in tests).
  Access tokens carry a `two_factor` claim for this.

App secrets are stored encrypted with AES-256-GCM. The key comes from `TWO_FACTOR_ENCRYPTION_KEY` (Base64 of 32
random bytes, `openssl rand -base64 32`); production refuses to start without it, and changing it disables every app
already set up. The dev and test profiles use fixed keys.

### Email verification and password recovery

Registering emails a link to confirm the address, and the account cannot sign in until it is followed: with the right
password, login answers `403 EMAIL_NOT_VERIFIED`, and with `"resendVerification": true` it also emails a new link.
Sign-up answers `202` with no body whether or not the address already has an account (the password is hashed either
way, so the timing matches too), so it cannot be used to find out who is registered. Only the owner learns about it:
an unverified account gets a new link, a verified one an email saying someone tried to sign up with it. Withdrawals
and transfers still check verification too (`403 EMAIL_NOT_VERIFIED`), for sessions opened before this rule.

Sign-up and password reset requests email any address they are given, so they also need a Cloudflare Turnstile token
(`captchaToken`, `422 CAPTCHA_FAILED` otherwise). It is on in production (`CAPTCHA_ENABLED`, with
`TURNSTILE_SECRET_KEY`; the web app needs the matching site key) and off in the dev and test profiles. Unlike the
breached password lookup it fails closed: if Cloudflare cannot be reached within 3 seconds, the request is refused.
Cloudflare's test keys (site key `1x00000000000000000000AA`, secret `1x0000000000000000000000000000000AA`) always pass
for trying it locally.

Links point to the client app
(`wallet.notifications.email.app-url`, `APP_URL` in production) with the token in the fragment
(`/verify-email#token=...`, `/reset-password#token=...`); the app posts the token to the endpoints above.
Each link works once and only the newest one works: verification links last 24 hours, reset links 30 minutes. Only a
hash of each token is stored, and tokens are never written to the event outbox. Locally the emails reach the real
inbox too, through Gmail.

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
| GET | `/api/v1/wallets/{id}/movements?page=0&size=20` | Statement, newest first, with the balance after each movement. Filters, all optional: `from` and `to` (calendar days, both included), `type` (repeatable: `DEPOSIT`, `TRANSFER`, `WITHDRAWAL`, `WITHDRAWAL_REVERSAL`...), `direction` (`IN`, `OUT`) and `q` (start of the folio, hex digits and dashes) |
| GET | `/api/v1/wallets/{id}/movements/{transactionId}` | One movement, for its receipt (`404 MOVEMENT_NOT_FOUND`) |
| GET | `/api/v1/wallets/{id}/statement?from=2026-11-01&to=2026-11-30` | The movements of a period (at most 366 days, the current month by default) as a CSV download, oldest first |
| GET | `/api/v1/wallets/{id}/summary?month=2026-11` | `openingBalance`, money in and out and `closingBalance` of a calendar month (the current one by default), summed over every movement |
| GET | `/api/v1/wallets/{id}/limits` | The daily outgoing limit, what was used today, what is left and when it resets |
| GET | `/api/v1/wallets/{id}/balance-history?days=30` | Closing balance of each of the last `days` days (1 to 366, today included), plus `openingBalance` from the day before; days before the wallet opened are left out. Days and months follow `wallet.accounts.limit-zone` |
| POST | `/api/v1/wallets/{id}/deposits` | Top-up through the payment provider (`{"amount": 100.50}`, at most 100,000.00 MXN or 5,000.00 USD per deposit, else `422 DEPOSIT_TOO_LARGE`); needs `Idempotency-Key` |
| GET | `/api/v1/wallets/{id}/deposits/{depositId}` | A deposit and its status, to follow it after the checkout |
| POST | `/api/v1/wallets/{id}/withdrawals` | Payout to the user's bank (`{"amount": 100, "bankAccountId": "..."}`, bank account required for MXN); needs `Idempotency-Key` |
| POST | `/api/v1/bank-accounts` | Add a CLABE (`{"clabe": "...", "holderName": "...", "twoFactorCode": "..."}`, the code only with two-factor on); at most 5, only the last four digits are returned |
| GET | `/api/v1/bank-accounts` | Your bank accounts |
| DELETE | `/api/v1/bank-accounts/{id}` | Remove one (`204`); withdrawals already on their way still arrive |
| GET | `/api/v1/wallets/{id}/withdrawals/{withdrawalId}` | A withdrawal and the status of its payout |

All of them require a bearer token. Amounts are returned as decimal strings (`{"amount": "100.50", "currency":
"MXN"}`) so clients never lose cents to floating point. Money is recorded in a double-entry ledger.

### Deposits and the payment provider

A deposit is credited only once the payment provider confirms the money, chosen with `PAYMENT_PROVIDER`:

- `simulated` (default outside production): the payment succeeds at once, so the deposit answers `201` with
  `"status": "COMPLETED"` and the new balance.
- `stripe`: the deposit answers `202` with `"status": "PENDING"` and a `checkoutUrl` on Stripe Checkout (cards only),
  valid for 30 minutes. Stripe sends the payer back to `wallet.payments.return-url` with `?payment=<deposit id>`, and
  the client polls the deposit until it becomes `COMPLETED`, `EXPIRED` or `REJECTED`.

Stripe confirms payments with a signed webhook to `POST /api/v1/payments/webhooks/stripe`; each event is applied once
however often it arrives, and a job asks Stripe about checkouts whose webhook never came. If the wallet was frozen or
the account disabled while the payer was paying, the deposit is `REJECTED`, nothing is credited and the payment is
refunded automatically. If Stripe cannot be reached the deposit fails with `503 PAYMENT_PROVIDER_UNAVAILABLE`.

To try Stripe locally with test keys and the [Stripe CLI](https://docs.stripe.com/stripe-cli):

```bash
stripe listen --forward-to localhost:8080/api/v1/payments/webhooks/stripe
```

Then start the app with `PAYMENT_PROVIDER=stripe`, `STRIPE_API_KEY=sk_test_...` and `STRIPE_WEBHOOK_SECRET` set to
the `whsec_...` value the command printed, open the `checkoutUrl` of a deposit and pay with card `4242 4242 4242 4242`.

### Withdrawals

The money leaves the wallet as soon as a withdrawal is requested and waits in a payouts-in-transit account until the
bank confirms the payout. The answer is `201` with `"status": "PAID"` when the payout is already confirmed (always,
with the simulated provider) or `202` with `"status": "PENDING"` while it is on its way. If the bank refuses the
payout, the withdrawal becomes `FAILED` with a `failureReason`, the money goes back to the wallet (statement type
`WITHDRAWAL_REVERSAL`) and the user is notified. A job sends again every minute the payouts the provider never
received and checks the ones on their way. The `PayoutStuck` alert fires when one has waited for over 30 minutes.

A bank account receives no withdrawals during its first 24 hours (`wallet.accounts.bank-account-cooling-off`):
someone who took over an account cannot add their own CLABE and empty the wallet before the owner reads the
"Bank account added" email. Withdrawals to it answer `422 BANK_ACCOUNT_COOLING_OFF` until then, and each bank
account shows `withdrawalsAvailableFrom`.

### Where the money is

The ledger follows the money to where it really sits, with these system accounts per currency:

| Account | Type | Holds |
|---|---|---|
| `SETTLEMENT` | asset | The platform's money at the partner bank; withdrawals are paid from it |
| `PROVIDER_CLEARING` | asset | Money Stripe collected and has not paid out to the bank yet |
| `UNAPPLIED_PAYMENTS` | liability | Money collected from a payer that no wallet has received yet, or that is being refunded |
| `PAYOUTS_IN_TRANSIT` | liability | Money taken from wallets for payouts the bank has not confirmed |
| `PROVIDER_FEES` | expense | What Stripe charged |
| `PLATFORM_CAPITAL` | equity | What the owners put into `SETTLEMENT` |

A payment that succeeds is recorded at once (`PAYMENT_RECEIVED`: into `PROVIDER_CLEARING`, or straight into
`SETTLEMENT` with the simulated provider, against `UNAPPLIED_PAYMENTS`); the deposit then moves it to the wallet, and
a refund sends it back (`PAYMENT_REFUNDED`). Every 15 minutes a job reads Stripe's balance transactions and records
what is new: fees (`PROVIDER_FEE`), payouts to the bank (`PROVIDER_PAYOUT`, from clearing to settlement) and failed
payouts. Charges and refunds the application caused are only recognised; anything else (disputes, adjustments,
refunds made from Stripe's dashboard) is left unmatched for a person. Afterwards the job compares Stripe's balance
with `PROVIDER_CLEARING`: the gap is the `wallet_provider_balance_difference` metric, and the
`ProviderBalanceMismatch` alert fires when it stays off for two hours.

Since Stripe pays out days after collecting, withdrawals are paid from capital in the meantime: an administrator
records each wire the owners send to the partner bank as a capital contribution. A payout the bank confirms while
`SETTLEMENT` cannot cover it stays `PENDING` and is recorded once capital or Stripe's payouts arrive. Likewise, a
refund larger than what `PROVIDER_CLEARING` holds (Stripe already paid that money out) is recorded against
`SETTLEMENT`, since Stripe then takes it from the bank account.

## Transfers

```http
POST /api/v1/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: 6f1c2a9e-3b7d-4c55-9a0e-2f8b1d4c7e10

{"sourceWalletId": "...", "recipientEmail": "bob@example.com", "amount": 30.25, "description": "Dinner"}
```

The recipient receives into their wallet in the sender's currency. `GET /api/v1/transfers/{id}` returns a transfer
to its sender or its recipient (`direction` is `SENT` or `RECEIVED`, with both names), and both statements show it
with the transfer id as `operationId`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/transfers/recipients?limit=8` | People you paid, most recently paid first (1 to 20), with how many times |
| GET | `/api/v1/transfers/recipient?email=&currency=MXN` | Who an email belongs to before sending, as a short name (`Ana M. L.`); `422 RECIPIENT_NOT_FOUND` if nobody with that email can receive in the currency. Limited to 20 lookups per minute per user |

Every request that moves money needs an `Idempotency-Key` (use a new UUID per user action):

| Situation | Response |
|---|---|
| First request | `201`, `Idempotent-Replayed: false` |
| Retry with the same key and body, even concurrent | Same `201` body, `Idempotent-Replayed: true`; money moves once |
| Same key, different body | `422 IDEMPOTENCY_KEY_REUSED` |
| Request failed (e.g. `INSUFFICIENT_FUNDS`) | Nothing is stored; the key can be retried |

## Limits and administration

- **Daily limit:** withdrawals and transfers sent share a limit per wallet and local day (Mexico City time):
  50,000.00 MXN and 3,000.00 USD by default (`wallet.accounts.daily-outgoing-limit`). Going over it returns
  `422 DAILY_LIMIT_EXCEEDED` with what is left for today. Deposits are not limited.
- **Administrator:** created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD` (dev profile:
  `admin@wallet.local` / `admin-dev-password`). An existing user with that email is never promoted.
  Before using the admin endpoints it sets up two-factor authentication like any user (`/api/v1/users/me/two-factor`)
  and signs in again with a code.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/admin/users/search` | Find a user by email (`{"email": "..."}`): status, verification, lockout |
| GET | `/api/v1/admin/users/{id}` | The same, by user id |
| GET | `/api/v1/admin/users/{id}/wallets` | The user's wallets with balance and status |
| POST | `/api/v1/admin/users/{id}/unlock` | Lift a lockout before it expires (`{"reason": "..."}`, required) |
| POST | `/api/v1/admin/users/{id}/disable` | Block login and every money movement, end all sessions (`{"reason": "..."}`, required) |
| POST | `/api/v1/admin/users/{id}/enable` | Allow them again (`{"reason": "..."}`) |
| POST | `/api/v1/admin/users/{id}/two-factor/reset` | Turn off two-factor authentication for a lost phone (`{"reason": "..."}`) |
| GET | `/api/v1/admin/wallets/{id}` | Any wallet with owner, balance and status |
| POST | `/api/v1/admin/wallets/{id}/freeze` | Block all money movement (`{"reason": "..."}`, required) |
| POST | `/api/v1/admin/wallets/{id}/unfreeze` | Allow it again (`{"reason": "..."}`) |
| GET | `/api/v1/admin/transfers/{id}`, `/deposits/{id}`, `/withdrawals/{id}` | Any operation, with its ledger entry |
| GET | `/api/v1/admin/audit-events?targetId=&actorId=&action=&from=&to=&page=&size=` | Audit log, newest first; `from` and `to` are instants, `to` excluded |
| GET | `/api/v1/admin/ledger/system-accounts` | Balances of the platform's own accounts, in minor units |
| POST | `/api/v1/admin/ledger/capital-contributions` | Record capital wired into settlement (`{"currency", "amount", "reference", "reason"}`); each bank `reference` once (`409 CAPITAL_CONTRIBUTION_EXISTS`) |
| GET | `/api/v1/admin/payments/provider-transactions?status=&limit=50` | Stripe balance movements, newest first; every status, or one of `PENDING`, `POSTED`, `UNMATCHED`, `RESOLVED` |
| POST | `/api/v1/admin/payments/provider-transactions/{provider}/{id}/resolve` | Close an unmatched movement (`{"reason": "..."}`, required); audited, posts nothing |
| GET | `/api/v1/admin/payments/provider-balance-differences` | The provider in use, whether it holds money, and per currency its balance against `PROVIDER_CLEARING` as of the last sync |

Every `/api/v1/admin/**` endpoint requires the `ADMIN` role (`403 FORBIDDEN` otherwise) and, outside tests, an
administrator with two-factor authentication on. Registrations, email
verifications, password changes and reset requests, lockouts and unlocks, account and wallet status changes and every
money movement are written to an append-only audit log through Spring Modulith's event outbox, so no entry is lost
even if the app crashes right after an operation.

A disabled account answers `403 ACCOUNT_DISABLED` on login, and its refresh tokens stop working. Access tokens issued
before stay valid for reads until they expire (at most 15 minutes), but every deposit, withdrawal or transfer checks
the account under a lock, so none goes through after the disable commits. The administrator account cannot be
disabled.

## Notifications

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/notifications?unreadOnly=false&page=0&size=20` | Your notifications, newest first |
| GET | `/api/v1/notifications/unread-count` | `{"count": 3}`, cheap enough to poll for a badge |
| POST | `/api/v1/notifications/{id}/read` | Mark one as read (`204`); repeating it keeps the first read time |
| POST | `/api/v1/notifications/read-all` | Mark all as read (`204`) |
| GET | `/api/v1/notifications/preferences` | Which notices also arrive by email: `emailMoneyIn`, `emailMoneyOut` (both on by default) |
| PUT | `/api/v1/notifications/preferences` | Change them (`{"emailMoneyIn": true, "emailMoneyOut": false}`) |
| GET | `/api/v1/notifications/stream` | Server-sent events: `ready` once connected, then `notification` (`{id, type, title}`) as each one arrives. Closes after 5 minutes (reconnect with a current token); a comment every 25 seconds keeps proxies from closing it |

Users are notified when they register, when failed logins lock their account, when their password is changed or
reset, when an administrator disables or enables their account, on every deposit, withdrawal and transfer (sent and
received), when a paid deposit is refunded because the wallet could not receive it, when the bank refuses a
withdrawal, when two-factor authentication is turned on or off, and when an administrator freezes or unfreezes one
of their wallets. Each one is also
emailed, unless the user turned off email for money received (deposits, transfers received) or sent (transfers
sent, withdrawals); security notices and failed operations are always emailed, and the inbox keeps everything. The
preference is read when the email is sent, so turning it off also stops emails still waiting in the outbox. The
stream is per instance: with several instances each one reaches only the apps connected to it, and the others see
the notification when they next poll. Notifications come from the event outbox like the audit log, so they appear a moment after the operation
and are never lost; the email is a separate step, so a mail server outage only delays the email. Notifications are
deleted after 90 days.

| Setting | Values |
|---|---|
| `wallet.notifications.email.delivery` | `smtp` (dev, tests and prod by default) or `log` (no email, only a log line) |
| `wallet.notifications.email.app-url` | Client app that opens the links in verification and reset emails |
| Production (`prod` profile) | `MAIL_USERNAME`, `MAIL_PASSWORD`, `EMAIL_FROM`, `APP_URL`; `MAIL_HOST` (`smtp.gmail.com` by default) and `MAIL_PORT` (587) only to use another server; `EMAIL_DELIVERY=log` turns email off |
| Gmail (dev and prod) | The default server (`smtp.gmail.com`, port 587): `MAIL_USERNAME` and an app password as `MAIL_PASSWORD` (see `.env.example`); dev reads them from `.env`. Gmail sends from `MAIL_USERNAME` and allows about 500 emails a day. Integration tests keep using Mailpit through Testcontainers |
| Payments in production | `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` (signing secret of the webhook endpoint in the Stripe dashboard); `PAYMENT_PROVIDER=simulated` for demos without real money |
| Payouts in production | `PAYOUT_PROVIDER` is required: the only provider so far is `simulated`, which sends no real money, so production refuses to start until it is set on purpose |

## Operations

### Failed events

If an event listener fails (audit, notifications or email), its publication stays in the outbox and a job retries
it every 30 seconds once its wait has passed: 1 minute after the first failure, then 2, 4... up to 1 hour.
Publications stuck for 10 minutes, e.g. because an instance died, are retried the same way. The
`wallet_events_pending` metric and its alert show when something is stuck.

### Ledger reconciliation

Every hour a job checks that each cached balance equals the sum of its postings, that every journal entry balances,
and that per currency assets plus expenses equal liabilities plus capital. It reads one consistent snapshot, so it never
blocks or misreads transfers in flight, and only one instance runs it at a time.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/reconciliations?limit=20` | Latest runs, newest first, with any discrepancies |
| POST | `/api/v1/admin/reconciliations` | Run one now (`409 RECONCILIATION_IN_PROGRESS` if one is running) |

Nightly jobs also delete expired refresh token families, expired email link tokens, idempotency keys older than
24 hours, daily usage rows older than 7 days, notifications older than 90 days and payment webhook ids older than
30 days. Every 5 minutes a job settles payments whose provider webhook never arrived, every 15 minutes another
follows Stripe's balance, and every minute another one moves pending withdrawals forward.

### Rate limits

| Requests | Limit | Counted per |
|---|---|---|
| `POST /api/v1/auth/password-reset` | 5 per 15 minutes | client address |
| `POST /api/v1/auth/register` | 15 per 15 minutes | client address |
| `POST /api/v1/users/me/email-verification` | 3 per 15 minutes | user |
| Any other `POST /api/v1/auth/**` | 20 per minute | client address |
| `POST` deposits, withdrawals, transfers | 30 per minute | user |
| `POST /api/v1/payments/webhooks/**` | 600 per minute | client address |
| `GET /api/v1/transfers/recipient` | 20 per minute | user |
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
| Grafana | http://localhost:3030 | "Digital Wallet" dashboard: traffic, latency, money moved, rejections, ledger and outbox health, pending and refused payouts, JVM and database pool (sign in as `admin`/`admin` to edit) |
| Prometheus | http://localhost:9090 | Raw metrics and alert rules (`docker/observability/prometheus/alerts.yml`) |
| Tempo | through Grafana, Explore > Tempo | Traces of each request down to every SQL statement |

On the management port, health and info are open for the orchestrator's probes; metrics and module details need HTTP
Basic `prometheus:<MANAGEMENT_SCRAPE_PASSWORD>` when that variable is set, which the prod profile requires. The
compose stack uses `scrape-dev-password` on both sides (`docker/observability/prometheus/prometheus.yml`).

The app started from the IDE is scraped on port 8081. It sends traces to Tempo on `localhost:4318` only when started
with `TRACE_EXPORT=true` (set it in the run configuration while the stack is up): without Tempo, every export would
log a connection error. Every response's `X-Correlation-Id` is also on its trace
(`correlation.id`), and log lines show `[correlationId,traceId,spanId]`. In latency panels, dots are exemplars:
click one to open that request's trace.

## Tests

```bash
./mvnw verify
```

Integration tests start a real PostgreSQL and Mailpit with Testcontainers, so Docker must be running.

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

