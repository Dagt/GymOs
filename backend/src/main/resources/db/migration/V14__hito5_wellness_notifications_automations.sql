CREATE TABLE professional (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    display_name VARCHAR(160) NOT NULL,
    professional_type VARCHAR(60) NOT NULL,
    email VARCHAR(180),
    phone VARCHAR(32),
    bio TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_professional_org_branch_status
    ON professional (organization_id, branch_id, status);

CREATE TABLE professional_service (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    professional_id UUID NOT NULL REFERENCES professional(id),
    name VARCHAR(160) NOT NULL,
    service_type VARCHAR(60) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    price_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_professional_service_professional
    ON professional_service (professional_id, status);

CREATE TABLE professional_availability (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    professional_id UUID NOT NULL REFERENCES professional(id),
    day_of_week INTEGER NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_professional_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_professional_availability_hours CHECK (closes_at > opens_at)
);

CREATE INDEX ix_professional_availability_professional
    ON professional_availability (professional_id, day_of_week);

CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    channel VARCHAR(40) NOT NULL,
    recipient VARCHAR(180),
    subject VARCHAR(180),
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(80),
    reference_type VARCHAR(80),
    reference_id UUID,
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_notification_org_branch_created
    ON notification_log (organization_id, branch_id, created_at);
CREATE INDEX ix_notification_org_status
    ON notification_log (organization_id, status, scheduled_at);

CREATE TABLE automation_rule (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    name VARCHAR(160) NOT NULL,
    trigger_type VARCHAR(80) NOT NULL,
    condition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_type VARCHAR(80) NOT NULL,
    action_config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL,
    retry_limit INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_automation_rule_org_branch_status
    ON automation_rule (organization_id, branch_id, status);

CREATE TABLE automation_execution (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    rule_id UUID NOT NULL REFERENCES automation_rule(id),
    status VARCHAR(32) NOT NULL,
    trigger_reference VARCHAR(180),
    error_message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_automation_execution_rule_occurred
    ON automation_execution (rule_id, occurred_at);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888961', 'professional.read', 'Permiso professional.read', 'wellness', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888962', 'professional.manage', 'Permiso professional.manage', 'wellness', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888963', 'notification.read', 'Permiso notification.read', 'notifications', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888964', 'notification.manage', 'Permiso notification.manage', 'notifications', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888965', 'automation.read', 'Permiso automation.read', 'automations', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888966', 'automation.manage', 'Permiso automation.manage', 'automations', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888967', 'report.owner.read', 'Permiso report.owner.read', 'reporting', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'professional.read',
    'professional.manage',
    'notification.read',
    'notification.manage',
    'automation.read',
    'automation.manage',
    'report.owner.read'
)
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO professional (
    id, organization_id, branch_id, display_name, professional_type, email, phone,
    bio, status, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999961',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Nutriologa Demo',
    'NUTRITIONIST',
    'nutricion@gymos.local',
    '+520000000011',
    'Profesional demo para consultas de nutricion.',
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO professional_service (
    id, organization_id, branch_id, professional_id, name, service_type,
    duration_minutes, price_cents, currency, status, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999962',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '99999999-9999-9999-9999-999999999961',
    'Consulta nutricional inicial',
    'NUTRITION',
    60,
    45000,
    'MXN',
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO professional_availability (
    id, organization_id, branch_id, professional_id, day_of_week, opens_at, closes_at,
    status, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999963',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '99999999-9999-9999-9999-999999999961',
    2,
    '09:00',
    '14:00',
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO automation_rule (
    id, organization_id, branch_id, name, trigger_type, condition_json,
    action_type, action_config_json, status, retry_limit, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999964',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Recordatorio de membresia por vencer',
    'MEMBERSHIP_EXPIRING',
    '{"daysBefore":3}'::jsonb,
    'CREATE_NOTIFICATION',
    '{"channel":"IN_APP","subject":"Membresia por vencer"}'::jsonb,
    'ENABLED',
    3,
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;
