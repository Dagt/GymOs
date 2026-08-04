ALTER TABLE notification_log
    ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'TRANSACTIONAL',
    ADD COLUMN recipient_type VARCHAR(32),
    ADD COLUMN recipient_id UUID,
    ADD COLUMN body_html TEXT,
    ADD COLUMN provider_key VARCHAR(40),
    ADD COLUMN provider_message_id VARCHAR(255),
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN available_at TIMESTAMPTZ,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE notification_log
SET available_at = COALESCE(scheduled_at, created_at),
    updated_at = created_at
WHERE available_at IS NULL OR updated_at IS NULL;

ALTER TABLE notification_log
    ALTER COLUMN available_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT ck_notification_attempt_count CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_notification_category CHECK (category IN ('TRANSACTIONAL', 'MARKETING')),
    ADD CONSTRAINT ck_notification_recipient_type CHECK (
        recipient_type IS NULL OR recipient_type IN ('CLIENT', 'USER', 'DIRECT')
    );

CREATE INDEX ix_notification_delivery_queue
    ON notification_log (available_at, organization_id, created_at)
    WHERE channel = 'EMAIL'
      AND status IN ('QUEUED', 'RETRY_SCHEDULED', 'PROCESSING');

CREATE TABLE notification_channel_configuration (
    organization_id UUID PRIMARY KEY REFERENCES organization(id),
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_provider_key VARCHAR(40) NOT NULL DEFAULT 'SMTP',
    from_email VARCHAR(180),
    from_name VARCHAR(160),
    reply_to VARCHAR(180),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE notification_recipient_preference (
    organization_id UUID NOT NULL REFERENCES organization(id),
    recipient_hash CHAR(64) NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, recipient_hash)
);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '8a000000-0000-0000-0000-000000000072',
    'notification.settings.manage',
    'Configurar canales y remitentes de notificaciones del gimnasio',
    'notifications', now(), now(), 0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code = 'notification.settings.manage'
WHERE role.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio')
ON CONFLICT DO NOTHING;
