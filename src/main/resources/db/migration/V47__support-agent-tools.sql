-- An agent who pauses gets no new cases; one without a row here is available
CREATE TABLE support_agent_availability (
    agent_id   UUID        PRIMARY KEY REFERENCES users (id),
    available  BOOLEAN     NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

-- How each case ended, and how urgent it was while it waited: problems with money go first
ALTER TABLE support_conversations ADD COLUMN resolution VARCHAR(20);
ALTER TABLE support_conversations ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE support_conversations ADD CONSTRAINT ck_support_conversations_resolution CHECK (resolution IN ('SOLVED',
    'INFORMATION', 'TICKET', 'NO_REPLY', 'DUPLICATE', 'CUSTOMER_CLOSED', 'OTHER'));
ALTER TABLE support_conversations ADD CONSTRAINT ck_support_conversations_priority CHECK (priority IN ('NORMAL', 'HIGH'));

DROP INDEX ix_support_conversations_queue;
CREATE INDEX ix_support_conversations_queue ON support_conversations (priority, escalated_at) WHERE status = 'QUEUED';
CREATE INDEX ix_support_conversations_assigned ON support_conversations (agent_id, assigned_at);

-- What the team writes on a case for each other; the customer never reads it
CREATE TABLE support_case_notes (
    id              UUID          PRIMARY KEY,
    conversation_id UUID          NOT NULL REFERENCES support_conversations (id) ON DELETE CASCADE,
    author_id       UUID          NOT NULL REFERENCES users (id),
    text            VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_support_case_notes_conversation ON support_case_notes (conversation_id, created_at);

-- A customer who stops answering is warned, and then the case is closed
ALTER TABLE support_messages DROP CONSTRAINT ck_support_messages_event;
ALTER TABLE support_messages ADD CONSTRAINT ck_support_messages_event CHECK (event IN ('ESCALATED', 'ASSIGNED',
    'RELEASED', 'TRANSFERRED', 'RESOLVED', 'TICKET_OPENED', 'TICKET_RESOLVED', 'INACTIVITY_WARNING',
    'INACTIVITY_CLOSED'));

-- What an agent thinks the bot should answer by itself, for the administrators who keep its knowledge files
CREATE TABLE support_bot_suggestions (
    id              UUID          PRIMARY KEY,
    conversation_id UUID          REFERENCES support_conversations (id) ON DELETE SET NULL,
    agent_id        UUID          NOT NULL REFERENCES users (id),
    language        VARCHAR(2)    NOT NULL,
    question        VARCHAR(300)  NOT NULL,
    answer          VARCHAR(2000) NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    reviewed_by     UUID          REFERENCES users (id),
    reviewed_at     TIMESTAMPTZ,
    CONSTRAINT ck_support_bot_suggestions_status CHECK (status IN ('OPEN', 'ADDED', 'DISMISSED')),
    CONSTRAINT ck_support_bot_suggestions_review CHECK ((status = 'OPEN') = (reviewed_at IS NULL))
);

CREATE INDEX ix_support_bot_suggestions_status ON support_bot_suggestions (status, created_at DESC);
