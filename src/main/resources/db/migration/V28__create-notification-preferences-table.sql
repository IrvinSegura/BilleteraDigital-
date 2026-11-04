-- Which notifications a user also wants by email. No row means the defaults: every email on.
CREATE TABLE notification_preferences (
    user_id         UUID        PRIMARY KEY,
    email_money_in  BOOLEAN     NOT NULL,
    email_money_out BOOLEAN     NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
);
