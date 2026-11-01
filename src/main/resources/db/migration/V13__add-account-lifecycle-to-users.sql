-- Accounts can be disabled by an administrator, and must verify their email before money can leave them
ALTER TABLE users
    ADD COLUMN status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'));

-- The administrator is created by the system from configuration, so its address counts as verified. Existing users
-- stay unverified until they confirm their address, like new ones.
UPDATE users SET email_verified_at = created_at WHERE role = 'ADMIN';

-- Email links (verify the address, reset the password). Only the SHA-256 hash of each token is stored, and a token
-- is deleted when used or replaced by a newer link, so each one works once.
CREATE TABLE one_time_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_one_time_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_one_time_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_one_time_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT ck_one_time_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_one_time_tokens_user_purpose ON one_time_tokens (user_id, purpose);
-- Serves the cleanup job
CREATE INDEX ix_one_time_tokens_expires_at ON one_time_tokens (expires_at);

-- Sessions are ended per user when the password changes or the account is disabled
CREATE INDEX ix_refresh_tokens_user_id_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
