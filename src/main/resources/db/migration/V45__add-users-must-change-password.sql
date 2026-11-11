-- Set when an account starts with a password someone else generated, until its owner replaces it
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
