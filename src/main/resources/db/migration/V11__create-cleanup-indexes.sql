-- Serve the cleanup jobs, which delete by age instead of by primary key
CREATE INDEX ix_idempotency_keys_created_at ON idempotency_keys (created_at);
CREATE INDEX ix_wallet_daily_outgoing_day ON wallet_daily_outgoing (day);
CREATE INDEX ix_refresh_tokens_family_id_expires_at ON refresh_tokens (family_id, expires_at);
