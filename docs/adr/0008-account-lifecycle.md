# 8. Account lifecycle: email verification, password recovery and account administration

- Status: accepted
- Date: 2026-10-30

## Context

Anyone could register with someone else's email address and move money out of the account, and nobody had ever
proven they own their address (ADR 0005 refuses to promote an existing user to administrator for that reason).
A forgotten password had no way back, and the lockout of ADR 0002 can be triggered on purpose against a victim with
no way to lift it. Support staff could only reach an account through a wallet id or the audit log, could not stop a
compromised account other than by freezing its wallets one by one, and could not look up an operation by its id.

## Decision

- **Email verification.** Registration publishes `EmailVerificationRequested`; the notifications module emails a
  link. Until the user follows it, the account can sign in, open wallets, deposit and receive money, but cannot
  withdraw or send it (`403 EMAIL_NOT_VERIFIED`), so an account registered with another person's address cannot
  take money out. `POST /api/v1/users/me/email-verification` sends a new link. The administrator is created verified,
  and existing users stay unverified until they follow a link, like new ones.
- **One-time tokens.** Links carry a 256-bit random token; only its SHA-256 hash is stored in `one_time_tokens`,
  like refresh tokens. Each token works for one purpose (`VERIFY_EMAIL`, `RESET_PASSWORD`), is deleted when used,
  and issuing a new link deletes the user's older ones, so only the newest link works. Verification links last
  24 hours and reset links 30 minutes. Any bad token answers `422 INVALID_TOKEN`. A nightly job deletes expired
  tokens.
- **Tokens never enter the outbox.** The events carry only the user id. The email listener issues the token inside
  its own transaction, through the identity module's `AccountTokens` API, and sends the email: if sending fails, the
  token rolls back with it and the retry issues a fresh one. Serialized events in `event_publication` therefore never
  hold a usable secret, and a link's lifetime starts when it is sent. The link puts the token in the URL fragment
  (`https://app/verify-email#token=...`), which browsers never send to servers, and the client app posts it to the
  API; a `GET` that verifies would be triggered by mail scanners that open links.
- **Forgotten password.** `POST /api/v1/auth/password-reset` always answers `202`, whether or not the address is
  registered, and only enabled accounts get an email. `POST /api/v1/auth/password-reset/confirm` sets the new
  password, clears the lockout, revokes every refresh token and also verifies the email, since following the link
  proves the user owns it. A weak password is rejected before the token is used up.
- **Changing the password.** `POST /api/v1/users/me/password` needs the current password. A wrong one counts as a
  failed login, so a stolen access token cannot be used to guess the password without locking the account. On
  success every session is revoked and a new token pair is returned to the caller, so only that device stays signed
  in. A notification and an email tell the owner either way: if someone else did it, the owner hears through a
  channel the attacker does not control.
- **Account status.** `users.status` is `ACTIVE` or `DISABLED`. Administrators disable and enable accounts with a
  mandatory reason (`UserStatusChanged`, audited with the reason; the user is notified without it). Disabling revokes
  every refresh token and makes login answer `403 ACCOUNT_DISABLED`, but only after the password matched, so it does
  not reveal the status to someone guessing. The administrator account cannot be disabled. A disabled account can
  still receive transfers; nothing can leave it.
- **Immediate effect without a lookup per request.** Access tokens stay valid for up to 15 minutes (ADR 0002) and
  are still not checked against the database. Instead, every operation that moves money already runs a database
  transaction, and now starts by calling `UserDirectory.authorizeOutgoingMoney` (withdrawals, transfers sent) or
  `authorizeDeposit`. These take a shared row lock on the user (`SELECT ... FOR SHARE`), and disabling takes the
  exclusive lock, so a disable waits for operations in flight and rejects every one after it, the same pattern as
  wallet freezes in ADR 0005. The user row is locked before any wallet or ledger account, and no path locks them in
  the opposite order, so this adds no deadlock. Reads with an old access token keep working until it expires.
- **Support tools.** `POST /api/v1/admin/users/search` finds a user by email (in the body, so the address stays out
  of access logs and traces) and returns status, verification and lockout. `GET /api/v1/admin/users/{id}`,
  `POST .../unlock`, `.../disable` and `.../enable` act on the account. The accounts module serves
  `GET /api/v1/admin/users/{id}/wallets`, keeping identity free of dependencies on other modules, and the transfers
  module serves `GET /api/v1/admin/transfers|deposits|withdrawals/{id}` with the ledger entry of each.
- **Events.** `EmailVerified`, `PasswordResetRequested`, `PasswordChanged` (`CHANGE` or `RESET`), `UserUnlocked` and
  `UserStatusChanged` feed the audit log; `PasswordChanged` and `UserStatusChanged` also become notifications.
- **Rate limits.** Requesting a reset is limited to 5 per 15 minutes per address and resending the verification to
  3 per 15 minutes per user, on top of the general auth limit, because each one sends an email. Resending requires
  signing in, so it is counted per user rather than per address.

## Alternatives considered

- **Carrying the token in the event** is simpler, but the outbox would store usable secrets until the email is
  sent, and failed publications keep them for much longer.
- **A token derived from a server key (HMAC)** would let the event carry only an id, at the cost of a new secret to
  manage and rotate. Issuing the token in the listener needs neither.
- **An access token deny list or a database check on every request** would make disabling instant for reads too, but
  costs a lookup per request on every instance. Money is what must stop at once, and those requests already reach the
  database.

## Consequences

- Users must follow the emailed link before their first withdrawal or transfer. Existing unverified users keep
  receiving money and can ask for a new link at any time.
- A reset request for a registered address writes an event while an unknown one does not, a timing difference of a
  few milliseconds; the per-address rate limit keeps it from being useful for enumeration.
- A victim locked out on purpose can now reset their password or ask support to unlock the account, which answers the
  risk accepted in ADR 0002.
- A login of a user waits for that user's money movements in flight, which take milliseconds.
- The administrator is still never created by promoting an existing user, even a verified one: granting that role
  stays a deliberate step outside the API.
- Verification levels with their own limits (ADR 0005) are still not modelled; `status` and `email_verified_at` are
  where such a level would start.
