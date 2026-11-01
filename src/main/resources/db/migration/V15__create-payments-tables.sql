-- Money collected through a payment provider. The id is chosen by the operation being paid for (a deposit shares
-- its id), so there is no foreign key to another module's table.
CREATE TABLE payments (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    amount_minor        BIGINT        NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    description         VARCHAR(140)  NOT NULL,
    provider            VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    -- The provider's checkout, e.g. a Stripe Checkout Session id; set once the payment is started
    provider_reference  VARCHAR(255),
    checkout_url        VARCHAR(2048),
    expires_at          TIMESTAMPTZ,
    -- The provider's id for the collected money (a Stripe PaymentIntent); refunds are made against it
    provider_payment_id VARCHAR(255),
    provider_refund_id  VARCHAR(255),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL,
    CONSTRAINT uk_payments_provider_reference UNIQUE (provider, provider_reference),
    CONSTRAINT ck_payments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED',
        'REFUND_PENDING', 'REFUNDED')),
    -- Failed payments may never have reached the provider
    CONSTRAINT ck_payments_started_has_reference
        CHECK (status IN ('CREATED', 'FAILED') OR provider_reference IS NOT NULL),
    CONSTRAINT ck_payments_collected_has_payment_id
        CHECK (status NOT IN ('SUCCEEDED', 'REFUND_PENDING', 'REFUNDED') OR provider_payment_id IS NOT NULL)
);

-- Serve the stale payment job, which looks only at payments still waiting for something
CREATE INDEX ix_payments_created_created_at ON payments (created_at) WHERE status = 'CREATED';
CREATE INDEX ix_payments_pending_expires_at ON payments (expires_at) WHERE status = 'PENDING';

-- Provider webhook events already processed. Providers deliver an event more than once, so its id is recorded with
-- its effect and a redelivery is skipped. Rows only need to outlive the provider's redelivery window.
CREATE TABLE payment_webhook_events (
    provider    VARCHAR(20)  NOT NULL,
    event_id    VARCHAR(255) NOT NULL,
    type        VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (provider, event_id)
);

CREATE INDEX ix_payment_webhook_events_received_at ON payment_webhook_events (received_at);
