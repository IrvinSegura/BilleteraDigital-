-- Authenticator apps (TOTP). The secret is encrypted by the application with a key the database never sees, so a
-- database leak alone cannot produce codes. last_used_step stops a code from being used twice.
CREATE TABLE user_two_factor (
    user_id          UUID         PRIMARY KEY,
    encrypted_secret VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    last_used_step   BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL,
    enabled_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL,
    CONSTRAINT fk_user_two_factor_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_two_factor_status CHECK (status IN ('PENDING', 'ENABLED')),
    CONSTRAINT ck_user_two_factor_enabled CHECK ((status = 'ENABLED') = (enabled_at IS NOT NULL))
);

-- Single-use codes for a lost phone; only their SHA-256 hash is stored
CREATE TABLE user_recovery_codes (
    user_id    UUID        NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT pk_user_recovery_codes PRIMARY KEY (user_id, code_hash),
    CONSTRAINT fk_user_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED',
    'DEPOSIT_RECEIVED', 'WITHDRAWAL_SENT', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'WALLET_FROZEN', 'WALLET_UNFROZEN',
    'PASSWORD_CHANGED', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED', 'DEPOSIT_REJECTED', 'WITHDRAWAL_FAILED',
    'BANK_ACCOUNT_ADDED', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED'));
