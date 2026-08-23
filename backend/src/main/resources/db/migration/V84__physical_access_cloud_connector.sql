-- GymFort Cloud owns commercial access rights and durable delivery state. The local
-- connector owns provider network details, provider credentials and biometric data.

CREATE UNIQUE INDEX ux_access_provider_connection_tenant_identity
    ON access_provider_connection (id, organization_id, branch_id);

CREATE UNIQUE INDEX ux_branch_tenant_identity
    ON branch (id, organization_id);

CREATE UNIQUE INDEX ux_access_device_tenant_branch_identity
    ON access_device (id, organization_id, branch_id);

CREATE UNIQUE INDEX ux_client_tenant_identity
    ON client (id, organization_id);

CREATE UNIQUE INDEX ux_benefit_tenant_identity
    ON benefit (id, organization_id);

ALTER TABLE access_provider_connection
    ADD CONSTRAINT fk_access_provider_connection_tenant_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id);

ALTER TABLE access_device
    ADD CONSTRAINT fk_access_device_tenant_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id);

ALTER TABLE access_provider_benefit_mapping
    ADD CONSTRAINT fk_access_provider_benefit_mapping_tenant_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_access_provider_benefit_mapping_tenant_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
        ON DELETE RESTRICT;

ALTER TABLE access_provider_client_link
    ADD CONSTRAINT fk_access_provider_client_link_tenant_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_access_provider_client_link_tenant_client
        FOREIGN KEY (client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE CASCADE;

ALTER TABLE access_provider_sync_state
    ADD CONSTRAINT fk_access_provider_sync_state_tenant_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_access_provider_sync_state_tenant_client
        FOREIGN KEY (client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE CASCADE;

UPDATE access_provider_connection
SET connector_key = NULL
WHERE connector_key IS NOT NULL
  AND BTRIM(connector_key) = '';

WITH duplicate_connector_keys AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(connector_key)
               ORDER BY created_at, id
           ) AS duplicate_rank
    FROM access_provider_connection
    WHERE connector_key IS NOT NULL
)
UPDATE access_provider_connection connection
SET connector_key = NULL
FROM duplicate_connector_keys duplicate
WHERE connection.id = duplicate.id
  AND duplicate.duplicate_rank > 1;

CREATE UNIQUE INDEX ux_access_provider_connector_key
    ON access_provider_connection (LOWER(connector_key))
    WHERE connector_key IS NOT NULL;

CREATE UNIQUE INDEX ux_access_provider_connection_branch
    ON access_provider_connection (organization_id, branch_id);

ALTER TABLE access_provider_connection
    ADD COLUMN connector_activated_at TIMESTAMPTZ,
    ADD COLUMN connector_reported_status VARCHAR(24),
    ADD COLUMN connector_version VARCHAR(80),
    ADD COLUMN last_connector_instance_id VARCHAR(120),
    ADD CONSTRAINT ck_access_provider_connector_reported_status
        CHECK (connector_reported_status IS NULL OR connector_reported_status IN ('HEALTHY', 'DEGRADED'));

CREATE TABLE access_provider_device_link (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    access_device_id UUID NOT NULL,
    external_device_ref VARCHAR(180) NOT NULL,
    external_device_name VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_provider_device_link_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_device_link_device
        FOREIGN KEY (access_device_id, organization_id, branch_id)
        REFERENCES access_device(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT ux_access_provider_device_link_device
        UNIQUE (connection_id, access_device_id),
    CONSTRAINT ux_access_provider_device_link_external
        UNIQUE (connection_id, external_device_ref)
);

CREATE INDEX ix_access_provider_device_link_tenant
    ON access_provider_device_link (organization_id, branch_id, access_device_id);

CREATE TABLE access_connector_activation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    activation_code_hash VARCHAR(100) NOT NULL,
    purpose VARCHAR(24) NOT NULL DEFAULT 'ACTIVATION',
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    CONSTRAINT fk_access_connector_activation_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_access_connector_activation_purpose
        CHECK (purpose IN ('ACTIVATION', 'ROTATION'))
);

CREATE UNIQUE INDEX ux_access_connector_activation_open
    ON access_connector_activation (connection_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_access_connector_activation_lookup
    ON access_connector_activation (connection_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE TABLE access_connector_credential (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    secret_hash VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_authenticated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_connector_credential_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_access_connector_credential_status
        CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE UNIQUE INDEX ux_access_connector_credential_active
    ON access_connector_credential (connection_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_access_connector_credential_lookup
    ON access_connector_credential (connection_id, status);

CREATE UNIQUE INDEX ux_access_connector_credential_tenant_identity
    ON access_connector_credential (id, organization_id, branch_id, connection_id);

ALTER TABLE access_provider_sync_state
    ADD COLUMN desired_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN blocked_reason_code VARCHAR(80),
    ADD COLUMN source_event_key VARCHAR(240);

CREATE UNIQUE INDEX ux_access_provider_sync_state_tenant_identity
    ON access_provider_sync_state (id, organization_id, branch_id, connection_id, client_id);

CREATE TABLE access_provider_sync_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    sync_state_id UUID NOT NULL,
    client_id UUID NOT NULL,
    desired_version BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    external_person_ref VARCHAR(180) NOT NULL,
    desired_access_level_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key VARCHAR(180) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    retryable BOOLEAN NOT NULL DEFAULT TRUE,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    claimed_by_credential_id UUID,
    claim_token UUID,
    completed_at TIMESTAMPTZ,
    external_operation_ref VARCHAR(180),
    last_error VARCHAR(1000),
    request_source VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_provider_sync_job_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_sync_job_state
        FOREIGN KEY (sync_state_id, organization_id, branch_id, connection_id, client_id)
        REFERENCES access_provider_sync_state(id, organization_id, branch_id, connection_id, client_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_sync_job_client
        FOREIGN KEY (client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_sync_job_claimed_credential
        FOREIGN KEY (claimed_by_credential_id, organization_id, branch_id, connection_id)
        REFERENCES access_connector_credential(id, organization_id, branch_id, connection_id)
        ON DELETE SET NULL (claimed_by_credential_id),
    CONSTRAINT ux_access_provider_sync_job_idempotency
        UNIQUE (connection_id, idempotency_key),
    CONSTRAINT ck_access_provider_sync_job_action
        CHECK (action IN ('UPSERT', 'REVOKE', 'RECONCILE')),
    CONSTRAINT ck_access_provider_sync_job_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_access_provider_sync_job_claim_token
        CHECK ((status = 'CLAIMED') = (claim_token IS NOT NULL)),
    CONSTRAINT ck_access_provider_sync_job_levels
        CHECK (jsonb_typeof(desired_access_level_refs) = 'array'),
    CONSTRAINT ck_access_provider_sync_job_attempts
        CHECK (attempts >= 0)
);

CREATE INDEX ix_access_provider_sync_job_claim
    ON access_provider_sync_job (connection_id, status, available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'CLAIMED');

CREATE INDEX ix_access_provider_sync_job_client
    ON access_provider_sync_job (organization_id, branch_id, client_id, created_at DESC);

ALTER TABLE access_provider_sync_state
    ADD COLUMN latest_job_id UUID REFERENCES access_provider_sync_job(id) ON DELETE SET NULL;

-- Preserve pending V83 desired states: safe/linked states become durable V84 jobs,
-- while states that cannot be delivered are made explicitly BLOCKED.
UPDATE access_provider_sync_state state
SET status = 'BLOCKED',
    blocked_reason_code = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM access_provider_client_link link
            WHERE link.organization_id = state.organization_id
              AND link.branch_id = state.branch_id
              AND link.connection_id = state.connection_id
              AND link.client_id = state.client_id
        ) THEN 'EXTERNAL_PERSON_LINK_REQUIRED'
        ELSE 'CONNECTION_DISABLED'
    END,
    last_error = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM access_provider_client_link link
            WHERE link.organization_id = state.organization_id
              AND link.branch_id = state.branch_id
              AND link.connection_id = state.connection_id
              AND link.client_id = state.client_id
        ) THEN 'Falta vincular al socio con la persona del proveedor fisico.'
        ELSE 'La conexion fisica esta desactivada.'
    END,
    source_event_key = COALESCE(source_event_key, 'migration-v84:block:' || state.id),
    updated_at = NOW(),
    version = state.version + 1
FROM access_provider_connection connection
WHERE connection.id = state.connection_id
  AND connection.organization_id = state.organization_id
  AND connection.branch_id = state.branch_id
  AND state.status IN ('PENDING', 'FAILED')
  AND (
      connection.enabled = FALSE
      OR NOT EXISTS (
          SELECT 1
          FROM access_provider_client_link link
          WHERE link.organization_id = state.organization_id
            AND link.branch_id = state.branch_id
            AND link.connection_id = state.connection_id
            AND link.client_id = state.client_id
      )
  );

INSERT INTO access_provider_sync_job (
    id, organization_id, branch_id, connection_id, sync_state_id, client_id,
    desired_version, action, external_person_ref, desired_access_level_refs,
    idempotency_key, status, attempts, retryable, available_at, request_source,
    created_at, updated_at, version
)
SELECT gen_random_uuid(), state.organization_id, state.branch_id, state.connection_id,
       state.id, state.client_id, state.desired_version, state.action,
       link.external_person_ref, state.desired_access_level_refs,
       'physical-access:migration-v84:' || state.id || ':' || state.desired_version,
       'PENDING', 0, TRUE, NOW(), 'MIGRATION_V84', NOW(), NOW(), 0
FROM access_provider_sync_state state
JOIN access_provider_connection connection
  ON connection.id = state.connection_id
 AND connection.organization_id = state.organization_id
 AND connection.branch_id = state.branch_id
JOIN access_provider_client_link link
  ON link.connection_id = state.connection_id
 AND link.organization_id = state.organization_id
 AND link.branch_id = state.branch_id
 AND link.client_id = state.client_id
WHERE state.status IN ('PENDING', 'FAILED')
  AND connection.enabled = TRUE
ON CONFLICT (connection_id, idempotency_key) DO NOTHING;

UPDATE access_provider_sync_state state
SET latest_job_id = job.id,
    status = 'PENDING',
    last_error = NULL,
    blocked_reason_code = NULL,
    source_event_key = COALESCE(state.source_event_key, 'migration-v84:queued:' || state.id),
    updated_at = NOW(),
    version = state.version + 1
FROM access_provider_sync_job job
WHERE job.sync_state_id = state.id
  AND job.request_source = 'MIGRATION_V84';

CREATE TABLE access_provider_person_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    external_person_ref VARCHAR(180) NOT NULL,
    external_display_name VARCHAR(180),
    email VARCHAR(254),
    phone VARCHAR(40),
    external_employee_number VARCHAR(120),
    match_status VARCHAR(24) NOT NULL,
    suggested_client_id UUID,
    matched_client_id UUID,
    match_reason VARCHAR(240),
    rejected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_provider_person_candidate_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_person_candidate_suggestion
        FOREIGN KEY (suggested_client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE SET NULL (suggested_client_id),
    CONSTRAINT fk_access_provider_person_candidate_match
        FOREIGN KEY (matched_client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE SET NULL (matched_client_id),
    CONSTRAINT ux_access_provider_person_candidate_external
        UNIQUE (connection_id, external_person_ref),
    CONSTRAINT ck_access_provider_person_candidate_status
        CHECK (match_status IN ('MATCHED', 'SUGGESTED', 'UNMATCHED', 'CONFLICT', 'REJECTED'))
);

CREATE INDEX ix_access_provider_person_candidate_status
    ON access_provider_person_candidate (organization_id, branch_id, connection_id, match_status, updated_at DESC);

ALTER TABLE access_log
    ADD COLUMN provider_connection_id UUID,
    ADD COLUMN provider_key VARCHAR(60),
    ADD COLUMN external_event_id VARCHAR(180),
    ADD COLUMN external_person_ref VARCHAR(180),
    ADD COLUMN external_device_ref VARCHAR(180),
    ADD COLUMN external_event_type VARCHAR(80),
    ADD COLUMN external_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT ck_access_log_external_metadata_object
        CHECK (jsonb_typeof(external_metadata_json) = 'object');

ALTER TABLE access_log
    ADD CONSTRAINT fk_access_log_tenant_provider_connection
        FOREIGN KEY (provider_connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE SET NULL (provider_connection_id);

CREATE UNIQUE INDEX ux_access_log_external_event
    ON access_log (organization_id, branch_id, provider_connection_id, external_event_id)
    WHERE provider_connection_id IS NOT NULL AND external_event_id IS NOT NULL;

CREATE INDEX ix_access_log_external_refs
    ON access_log (organization_id, branch_id, provider_connection_id, external_person_ref, external_device_ref)
    WHERE provider_connection_id IS NOT NULL;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '88888888-8888-8888-8888-888888888904',
    'access.physical.manage',
    'Administrar proveedores, conectores y mapeos de acceso fisico',
    'access',
    NOW(),
    NOW(),
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'access.physical.manage'
WHERE r.deleted_at IS NULL
  AND r.name IN ('Dueno del gimnasio', 'Gerente')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'access.manual.open'
WHERE r.deleted_at IS NULL
  AND r.name IN ('Dueno del gimnasio', 'Gerente')
ON CONFLICT DO NOTHING;
