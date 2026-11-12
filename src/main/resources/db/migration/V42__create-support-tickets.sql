-- Problems with a customer's money that support passes to the administrators: the agent who attends the customer
-- opens one about an operation, and an administrator settles it with the operations tools
CREATE TABLE support_tickets (
    id              UUID          PRIMARY KEY,
    conversation_id UUID          NOT NULL REFERENCES support_conversations (id),
    user_id         UUID          NOT NULL REFERENCES users (id),
    opened_by       UUID          NOT NULL REFERENCES users (id),
    operation_kind  VARCHAR(20)   NOT NULL,
    operation_id    UUID          NOT NULL,
    description     VARCHAR(1000) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    resolution      VARCHAR(500),
    resolved_by     UUID          REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL,
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT ck_support_tickets_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_support_tickets_kind CHECK (operation_kind IN ('deposit', 'withdrawal', 'transfer_out', 'transfer_in')),
    CONSTRAINT ck_support_tickets_resolved CHECK ((status = 'RESOLVED')
        = (resolution IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);

-- One open ticket per operation, whoever opens it: two could get the money fixed twice
CREATE UNIQUE INDEX ux_support_tickets_open_operation ON support_tickets (operation_id) WHERE status = 'OPEN';
CREATE INDEX ix_support_tickets_open ON support_tickets (created_at) WHERE status = 'OPEN';
CREATE INDEX ix_support_tickets_status ON support_tickets (status, created_at DESC);
CREATE INDEX ix_support_tickets_conversation ON support_tickets (conversation_id);

ALTER TABLE support_messages DROP CONSTRAINT ck_support_messages_event;
ALTER TABLE support_messages ADD CONSTRAINT ck_support_messages_event
    CHECK (event IN ('ESCALATED', 'ASSIGNED', 'RELEASED', 'RESOLVED', 'TICKET_OPENED', 'TICKET_RESOLVED'));

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED',
    'DEPOSIT_RECEIVED', 'WITHDRAWAL_SENT', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'WALLET_FROZEN', 'WALLET_UNFROZEN',
    'PASSWORD_CHANGED', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED', 'DEPOSIT_REJECTED', 'WITHDRAWAL_FAILED',
    'BANK_ACCOUNT_ADDED', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'PHONE_CHANGED', 'ACCOUNT_UNLOCKED',
    'BANK_ACCOUNT_REMOVED', 'SESSIONS_REVOKED', 'SUPPORT_CASE_ASSIGNED', 'SUPPORT_AGENT_JOINED',
    'SUPPORT_AGENT_REPLIED', 'SUPPORT_CASE_RESOLVED', 'SUPPORT_TICKET_RESOLVED'));
