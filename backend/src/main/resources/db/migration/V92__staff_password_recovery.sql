ALTER TABLE user_account ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0
    CHECK (credential_version >= 0);

CREATE TABLE user_password_reset (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'CONSUMED', 'REVOKED')),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    email_delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (email_delivery_status IN ('PENDING', 'SENT', 'FAILED', 'NOT_CONFIGURED', 'NO_EMAIL')),
    CHECK ((status = 'CONSUMED') = (consumed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_user_password_reset_pending ON user_password_reset (user_id) WHERE status = 'PENDING';
CREATE INDEX ix_user_password_reset_user_created ON user_password_reset (user_id, created_at DESC);
