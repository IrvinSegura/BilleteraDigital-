-- A wallet's movements are searched by the people and concepts of its transfers, sent and received
CREATE INDEX ix_transfers_source_wallet_created_at ON transfers (source_wallet_id, created_at DESC);
CREATE INDEX ix_transfers_destination_wallet_created_at ON transfers (destination_wallet_id, created_at DESC);
