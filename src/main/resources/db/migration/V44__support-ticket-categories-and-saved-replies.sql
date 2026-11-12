-- Tickets about the account itself, not an operation: a sign-in lock, an INE waiting too long for review
ALTER TABLE support_tickets ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OPERATION';
ALTER TABLE support_tickets ALTER COLUMN category DROP DEFAULT;
ALTER TABLE support_tickets ALTER COLUMN operation_kind DROP NOT NULL;
ALTER TABLE support_tickets ALTER COLUMN operation_id DROP NOT NULL;

ALTER TABLE support_tickets
    ADD CONSTRAINT ck_support_tickets_category CHECK (category IN ('OPERATION', 'ACCOUNT_ACCESS', 'IDENTITY', 'OTHER'));
-- Only tickets about an operation point at one, and then with its kind
ALTER TABLE support_tickets ADD CONSTRAINT ck_support_tickets_operation CHECK (
    (category = 'OPERATION') = (operation_id IS NOT NULL) AND (operation_id IS NULL) = (operation_kind IS NULL));

-- The administrators' adjustments are operations the customer may ask about too
ALTER TABLE support_tickets DROP CONSTRAINT ck_support_tickets_kind;
ALTER TABLE support_tickets ADD CONSTRAINT ck_support_tickets_kind CHECK (operation_kind IN ('deposit', 'withdrawal',
    'transfer_out', 'transfer_in', 'adjustment_credit', 'adjustment_debit'));

-- One open ticket per customer and subject, as there is one per operation
CREATE UNIQUE INDEX ux_support_tickets_open_category ON support_tickets (user_id, category)
    WHERE status = 'OPEN' AND category <> 'OPERATION';

-- An agent may pass their case to a colleague
ALTER TABLE support_messages DROP CONSTRAINT ck_support_messages_event;
ALTER TABLE support_messages ADD CONSTRAINT ck_support_messages_event CHECK (event IN ('ESCALATED', 'ASSIGNED',
    'RELEASED', 'TRANSFERRED', 'RESOLVED', 'TICKET_OPENED', 'TICKET_RESOLVED'));

-- Answers each agent keeps to send again
CREATE TABLE support_saved_replies (
    id         UUID          PRIMARY KEY,
    agent_id   UUID          NOT NULL REFERENCES users (id),
    title      VARCHAR(60)   NOT NULL,
    text       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_support_saved_replies_agent ON support_saved_replies (agent_id);
