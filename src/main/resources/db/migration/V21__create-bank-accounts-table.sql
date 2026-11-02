-- Bank accounts users withdraw MXN to, identified by their CLABE. Removed ones are kept, because withdrawals already
-- on their way still pay into them; a CLABE can be added again once its previous row is removed.
CREATE TABLE bank_accounts (
    id          UUID         PRIMARY KEY,
    owner_id    UUID         NOT NULL,
    clabe       VARCHAR(18)  NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    removed_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL,
    CONSTRAINT ck_bank_accounts_clabe_digits CHECK (clabe ~ '^[0-9]{18}$')
);

CREATE UNIQUE INDEX uk_bank_accounts_owner_clabe_active ON bank_accounts (owner_id, clabe) WHERE removed_at IS NULL;
