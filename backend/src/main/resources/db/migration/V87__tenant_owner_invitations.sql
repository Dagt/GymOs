CREATE TABLE user_invitation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id),
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    CONSTRAINT ck_user_invitation_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_user_invitation_acceptance CHECK (
        (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
        OR (status <> 'ACCEPTED' AND accepted_at IS NULL)
    )
);

CREATE UNIQUE INDEX ux_user_invitation_token_hash ON user_invitation (token_hash);
CREATE UNIQUE INDEX ux_user_invitation_pending_user
    ON user_invitation (user_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_user_invitation_pending_expiry
    ON user_invitation (expires_at)
    WHERE status = 'PENDING';
