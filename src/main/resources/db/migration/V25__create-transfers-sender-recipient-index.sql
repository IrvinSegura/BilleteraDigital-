-- The step-up check asks whether a sender has paid a recipient before, on every transfer
CREATE INDEX ix_transfers_sender_recipient ON transfers (sender_id, recipient_id);
