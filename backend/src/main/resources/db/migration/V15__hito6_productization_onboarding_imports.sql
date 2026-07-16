CREATE TABLE client_import_batch (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    file_name VARCHAR(180),
    dry_run BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_client_import_batch_org_created
    ON client_import_batch (organization_id, created_at);

CREATE TABLE client_import_row (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES client_import_batch(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    row_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason TEXT,
    client_id UUID REFERENCES client(id),
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    email VARCHAR(180),
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_client_import_row_batch
    ON client_import_row (batch_id, row_number);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888971', 'onboarding.manage', 'Permiso onboarding.manage', 'onboarding', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888972', 'client.import', 'Permiso client.import', 'clients', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('onboarding.manage', 'client.import')
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;
