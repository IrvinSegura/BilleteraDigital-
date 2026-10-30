-- In-app notifications, written by the notifications module from other modules' events. user_id and reference_id
-- (the deposit, withdrawal, transfer or wallet the notification is about) point to other modules' tables without
-- foreign keys. Unlike the audit log, rows change (read_at) and expire, so the table is not append-only.
CREATE TABLE notifications (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    body         VARCHAR(500) NOT NULL,
    reference_id UUID,
    created_at   TIMESTAMPTZ  NOT NULL,
    read_at      TIMESTAMPTZ,
    CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED', 'DEPOSIT_RECEIVED',
        'WITHDRAWAL_SENT', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'WALLET_FROZEN', 'WALLET_UNFROZEN'))
);

-- Serves the inbox, newest first
CREATE INDEX ix_notifications_user_created_at ON notifications (user_id, created_at DESC, id);
-- Serves the unread badge, which clients poll; only unread rows are indexed
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
-- Serves the cleanup job
CREATE INDEX ix_notifications_created_at ON notifications (created_at);
