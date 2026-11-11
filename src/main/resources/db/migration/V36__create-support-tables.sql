-- Support agents are one more kind of account, and tell users and agents what happens to a case
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN', 'SUPPORT'));

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED',
    'DEPOSIT_RECEIVED', 'WITHDRAWAL_SENT', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'WALLET_FROZEN', 'WALLET_UNFROZEN',
    'PASSWORD_CHANGED', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED', 'DEPOSIT_REJECTED', 'WITHDRAWAL_FAILED',
    'BANK_ACCOUNT_ADDED', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'PHONE_CHANGED', 'ACCOUNT_UNLOCKED',
    'BANK_ACCOUNT_REMOVED', 'SESSIONS_REVOKED', 'SUPPORT_CASE_ASSIGNED', 'SUPPORT_AGENT_JOINED',
    'SUPPORT_AGENT_REPLIED', 'SUPPORT_CASE_RESOLVED'));

CREATE TABLE support_conversations (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    status          VARCHAR(20)  NOT NULL,
    agent_id        UUID         REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    escalated_at    TIMESTAMPTZ,
    assigned_at     TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    last_message_at TIMESTAMPTZ  NOT NULL,
    survey_score    SMALLINT,
    survey_comment  VARCHAR(500),
    surveyed_at     TIMESTAMPTZ,
    CONSTRAINT ck_support_conversations_status CHECK (status IN ('BOT', 'QUEUED', 'ASSIGNED', 'RESOLVED')),
    CONSTRAINT ck_support_conversations_agent CHECK (status <> 'ASSIGNED' OR agent_id IS NOT NULL),
    CONSTRAINT ck_support_conversations_score CHECK (survey_score BETWEEN 1 AND 5)
);

-- The rules of the chat, kept by the database whatever the code does: a user has one open conversation, and an agent
-- attends one case at a time
CREATE UNIQUE INDEX ux_support_conversations_open_user ON support_conversations (user_id) WHERE status <> 'RESOLVED';
CREATE UNIQUE INDEX ux_support_conversations_active_agent ON support_conversations (agent_id) WHERE status = 'ASSIGNED';
CREATE INDEX ix_support_conversations_queue ON support_conversations (escalated_at) WHERE status = 'QUEUED';
CREATE INDEX ix_support_conversations_user ON support_conversations (user_id, resolved_at DESC);
CREATE INDEX ix_support_conversations_agent ON support_conversations (agent_id, last_message_at DESC);
CREATE INDEX ix_support_conversations_status ON support_conversations (status, last_message_at DESC);

-- In the database rather than on disk, so they are backed up with the conversation and work with any deployment
CREATE TABLE support_attachments (
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES support_conversations (id),
    file_name       VARCHAR(150) NOT NULL,
    content_type    VARCHAR(50)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    content         BYTEA        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_support_attachments_conversation ON support_attachments (conversation_id);

CREATE TABLE support_messages (
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES support_conversations (id),
    -- Messages of one instant keep the order they were written in
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY,
    sender          VARCHAR(10)  NOT NULL,
    sender_id       UUID         REFERENCES users (id),
    text            TEXT,
    event           VARCHAR(20),
    asks_feedback   BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_id   UUID         REFERENCES support_attachments (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_support_messages_sender CHECK (sender IN ('USER', 'BOT', 'AGENT', 'SYSTEM')),
    CONSTRAINT ck_support_messages_event CHECK (event IN ('ESCALATED', 'ASSIGNED', 'RELEASED', 'RESOLVED')),
    CONSTRAINT ck_support_messages_content CHECK (text IS NOT NULL OR event IS NOT NULL OR attachment_id IS NOT NULL)
);

CREATE INDEX ix_support_messages_conversation ON support_messages (conversation_id, seq);
