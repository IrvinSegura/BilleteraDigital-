-- The last comparison of the provider's balance with the clearing account, per currency. Stored so every instance
-- answers the same, and a restart does not make it look as if the provider reported nothing.
CREATE TABLE provider_balance_snapshots (
    provider               VARCHAR(20) NOT NULL,
    currency               VARCHAR(3)  NOT NULL,
    provider_balance_minor BIGINT      NOT NULL,
    recorded_balance_minor BIGINT      NOT NULL,
    compared_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_provider_balance_snapshots PRIMARY KEY (provider, currency)
);
