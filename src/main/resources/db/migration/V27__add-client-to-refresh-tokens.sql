-- Where each session was signed in from, so the user can recognize their sessions and end the ones they don't
ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(255);
ALTER TABLE refresh_tokens ADD COLUMN ip_address VARCHAR(45);
