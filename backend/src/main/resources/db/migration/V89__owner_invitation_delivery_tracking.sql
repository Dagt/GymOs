ALTER TABLE user_invitation
    ADD COLUMN email_delivery_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN email_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_email_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_email_sent_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_user_invitation_email_delivery_status CHECK (
        email_delivery_status IN ('UNKNOWN', 'PENDING', 'SENT', 'FAILED', 'NOT_CONFIGURED')
    ),
    ADD CONSTRAINT ck_user_invitation_email_attempt_count CHECK (email_attempt_count >= 0),
    ADD CONSTRAINT ck_user_invitation_email_sent_at CHECK (
        email_delivery_status <> 'SENT' OR last_email_sent_at IS NOT NULL
    );

CREATE INDEX ix_user_invitation_user_created
    ON user_invitation (user_id, created_at DESC);
