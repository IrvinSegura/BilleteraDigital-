-- Identity verification with a voter card (INE). The card's data and its pictures are encrypted by the application
-- (AES-256-GCM, key outside the database); only keyed fingerprints, which find a card already in use, and the CURP's
-- last four characters are in clear
CREATE TABLE identity_verifications (
    id                     UUID          PRIMARY KEY,
    user_id                UUID          NOT NULL REFERENCES users (id),
    status                 VARCHAR(20)   NOT NULL,
    document_type          VARCHAR(10)   NOT NULL DEFAULT 'INE',
    encrypted_data         TEXT          NOT NULL,
    curp_fingerprint       VARCHAR(64)   NOT NULL,
    voter_key_fingerprint  VARCHAR(64)   NOT NULL,
    curp_last_four         VARCHAR(4)    NOT NULL,
    names_match_curp       BOOLEAN       NOT NULL,
    name_matches_account   BOOLEAN       NOT NULL,
    front_image            BYTEA         NOT NULL,
    back_image             BYTEA         NOT NULL,
    selfie_image           BYTEA         NOT NULL,
    submitted_at           TIMESTAMPTZ   NOT NULL,
    reviewed_at            TIMESTAMPTZ,
    reviewed_by            UUID          REFERENCES users (id),
    rejection_reason       VARCHAR(300),
    CONSTRAINT ck_identity_verifications_status CHECK (status IN ('PENDING_REVIEW', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_identity_verifications_document CHECK (document_type IN ('INE')),
    CONSTRAINT ck_identity_verifications_review CHECK (status = 'PENDING_REVIEW'
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)),
    CONSTRAINT ck_identity_verifications_rejection CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- The rules, kept by the database whatever the code does: a user has at most one pending or verified identity, and a
-- card belongs to one account only
CREATE UNIQUE INDEX ux_identity_verifications_open_user ON identity_verifications (user_id)
    WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX ux_identity_verifications_curp ON identity_verifications (curp_fingerprint)
    WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX ux_identity_verifications_voter_key ON identity_verifications (voter_key_fingerprint)
    WHERE status <> 'REJECTED';
CREATE INDEX ix_identity_verifications_user ON identity_verifications (user_id, submitted_at DESC);
CREATE INDEX ix_identity_verifications_status ON identity_verifications (status, submitted_at);
