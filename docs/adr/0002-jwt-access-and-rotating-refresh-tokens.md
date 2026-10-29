# 2. Short-lived RS256 access tokens with rotating refresh tokens

- Status: accepted
- Date: 2026-10-29

## Context

The API is stateless and will run on several instances. Clients need to stay signed in for days, but a stolen
credential must stop working quickly. Login must also resist password guessing without revealing which emails exist.

## Decision

- **Access tokens** are JWTs signed with RS256, valid for 15 minutes. They carry only the user id (`sub`), roles,
  issuer and a unique `jti`; no personal data. Only the private key can sign, so other services could verify tokens
  with the public key alone.
- **Refresh tokens** are opaque 256-bit random values, valid for 7 days and stored only as SHA-256 hashes. Every
  login starts a *family*. Each refresh revokes the presented token and issues a new one in the same family.
- **Reuse detection**: presenting an already used refresh token revokes the whole family, because either the client
  or an attacker holds a stolen copy. Rows are locked (`SELECT ... FOR UPDATE`) so two concurrent refreshes cannot
  both succeed.
- **Logout** revokes the family of the given refresh token. Access tokens already issued stay valid until they
  expire (at most 15 minutes); there is no access token deny list.
- **Passwords** are hashed with BCrypt (cost 12) behind a `{bcrypt}` prefix so the algorithm can change later.
  Rules follow NIST SP 800-63B: 12 to 64 characters, no composition rules, at most 72 bytes (BCrypt's limit).
- **Lockout**: 5 consecutive failed logins lock the account for 15 minutes (`423 Locked` with `Retry-After`).
  Unknown emails and wrong passwords return the same `401 INVALID_CREDENTIALS` and take the same time, since a dummy
  hash is checked when the email does not exist. The lock itself does reveal that an account exists; registration
  already does too (`409`), so per-IP rate limiting (phase 5) is the real defense against enumeration.

## Consequences

- No session storage or database lookup on each request; any instance can verify tokens.
- Revoking access is not instant: up to 15 minutes for access tokens.
- Production must provide the RSA key pair (`JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION`). Dev and tests
  generate a temporary pair at startup, which logs everyone out on restart.
- Lockout can be abused to lock a victim out on purpose. Accepted for now and limited by the short lock duration.
- Expired and revoked refresh tokens accumulate; a cleanup job is planned with the reconciliation work (phase 5).
