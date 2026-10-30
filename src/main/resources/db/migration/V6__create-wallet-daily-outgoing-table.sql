-- Money that left each wallet per local day (withdrawals and transfers sent), used to enforce the daily limit.
-- Rows are only written while the wallet row is locked, so the read-check-add sequence is never interleaved.
CREATE TABLE wallet_daily_outgoing (
    wallet_id    UUID   NOT NULL,
    day          DATE   NOT NULL,
    amount_minor BIGINT NOT NULL,
    CONSTRAINT pk_wallet_daily_outgoing PRIMARY KEY (wallet_id, day),
    CONSTRAINT ck_wallet_daily_outgoing_amount_non_negative CHECK (amount_minor >= 0)
);
