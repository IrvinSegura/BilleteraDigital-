-- Users can close their own account; a closed one keeps its row, like everything that points to it, but never signs
-- in again
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'CLOSED'));

-- A link to confirm a new address carries the address it confirms, so nothing changes until it is followed
ALTER TABLE one_time_tokens ADD COLUMN new_email VARCHAR(254);
ALTER TABLE one_time_tokens DROP CONSTRAINT ck_one_time_tokens_purpose;
ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_purpose
    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'CHANGE_EMAIL'));
ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_new_email
    CHECK ((purpose = 'CHANGE_EMAIL') = (new_email IS NOT NULL));
