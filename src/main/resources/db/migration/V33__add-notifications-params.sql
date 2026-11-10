-- The figures behind a notification's text (amount, currency, counterpart...), so its email can be written in the
-- recipient's language. Rows from before this column have none and their email falls back to the stored text.
ALTER TABLE notifications ADD COLUMN params JSONB NOT NULL DEFAULT '{}'::jsonb;
