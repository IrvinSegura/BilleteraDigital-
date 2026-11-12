-- What the bot made of each message it read, and whether its answer helped: where it fails is what to add to its
-- knowledge files. The question itself is the user's message, so it is read from there and not copied
CREATE TABLE support_bot_questions (
    message_id      UUID         PRIMARY KEY REFERENCES support_messages (id),
    conversation_id UUID         NOT NULL REFERENCES support_conversations (id),
    language        VARCHAR(2)   NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    -- The section it answered with or, when it offered some, their titles one per line
    topic           TEXT,
    feedback        VARCHAR(10),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_support_bot_questions_language CHECK (language IN ('es', 'en')),
    CONSTRAINT ck_support_bot_questions_outcome CHECK (outcome IN ('ANSWERED', 'SMALL_TALK', 'PASSED_ON', 'SUGGESTED',
        'ASKED_AGAIN', 'NOT_FOUND')),
    CONSTRAINT ck_support_bot_questions_feedback CHECK (feedback IN ('HELPED', 'NOT_HELPED')),
    CONSTRAINT ck_support_bot_questions_feedback_answer CHECK (feedback IS NULL OR outcome = 'ANSWERED')
);

CREATE INDEX ix_support_bot_questions_created ON support_bot_questions (created_at DESC);
CREATE INDEX ix_support_bot_questions_conversation ON support_bot_questions (conversation_id, created_at DESC);
