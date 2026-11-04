-- Serves filtering the audit log by action, newest first
CREATE INDEX ix_audit_events_action ON audit_events (action, occurred_at DESC);
