-- The language the user's emails are written in, kept in step with the one the app shows. Spanish is the app's default.
ALTER TABLE users ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'es';
ALTER TABLE users ADD CONSTRAINT ck_users_language CHECK (language IN ('es', 'en'));
