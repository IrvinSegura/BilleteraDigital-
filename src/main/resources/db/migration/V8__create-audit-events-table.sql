-- Append-only record of security- and money-relevant actions, written by the audit module from other modules'
-- events. Actor and target ids point to other modules' tables without foreign keys.
CREATE TABLE audit_events (
    id          UUID         PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    -- Null when the system acted on its own, e.g. a lockout after failed logins
    actor_id    UUID,
    action      VARCHAR(50)  NOT NULL,
    target_type VARCHAR(30)  NOT NULL,
    target_id   UUID         NOT NULL,
    details     VARCHAR(500)
);

CREATE INDEX ix_audit_events_target ON audit_events (target_id, occurred_at DESC);
CREATE INDEX ix_audit_events_actor ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_events_occurred_at ON audit_events (occurred_at DESC);

-- Same protection as the ledger: an audit trail that can be edited proves nothing
CREATE FUNCTION reject_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only', TG_TABLE_NAME;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_change();

DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM %I', app_role);
    END IF;
END
$$;
