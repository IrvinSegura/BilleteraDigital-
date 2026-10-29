-- Tables created by the migration owner are readable and writable by the application role.
-- Ledger and audit migrations revoke UPDATE and DELETE on their own tables to keep them append-only.
-- The role is skipped when it does not exist (e.g. Testcontainers, where a single user owns everything).
DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', app_role);
    END IF;
END
$$;
