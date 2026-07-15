CREATE TABLE access_device (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    name VARCHAR(120) NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    device_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    adapter_config_json JSONB,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_access_device_org_code ON access_device (organization_id, device_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_access_device_org_branch_status ON access_device (organization_id, branch_id, status);

CREATE TABLE access_temporary_pass (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID NOT NULL REFERENCES client(id),
    status VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    max_uses INTEGER NOT NULL,
    uses_count INTEGER NOT NULL DEFAULT 0,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_access_temp_pass_org_branch_client ON access_temporary_pass (organization_id, branch_id, client_id, status);
CREATE INDEX ix_access_temp_pass_expires ON access_temporary_pass (expires_at);

CREATE TABLE access_log (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    device_id UUID REFERENCES access_device(id),
    client_id UUID REFERENCES client(id),
    membership_id UUID REFERENCES membership(id),
    temporary_pass_id UUID REFERENCES access_temporary_pass(id),
    access_type VARCHAR(40) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    source VARCHAR(40) NOT NULL,
    reason TEXT,
    command_status VARCHAR(40),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    correlation_id UUID NOT NULL
);

CREATE INDEX ix_access_log_org_branch_occurred ON access_log (organization_id, branch_id, occurred_at);
CREATE INDEX ix_access_log_client_occurred ON access_log (client_id, occurred_at);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888901', 'access.read', 'Permiso access.read', 'access', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888902', 'access.manage', 'Permiso access.manage', 'access', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888903', 'access.manual.open', 'Permiso access.manual.open', 'access', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('access.read', 'access.manage', 'access.manual.open')
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO access_device (
    id, organization_id, branch_id, name, device_code, device_type, status,
    provider, adapter_config_json, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999911',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Puerta principal',
    'MAIN-DOOR',
    'TURNSTILE',
    'ACTIVE',
    'SIMULATED',
    '{"mode":"simulated-open"}'::jsonb,
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;
