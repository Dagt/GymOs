-- Physical access integration is deliberately provider-neutral.  The local connector owns
-- network credentials and biometric processing; GymFort persists only opaque external IDs
-- and the desired authorization state.
CREATE TABLE access_provider_connection (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    provider_key VARCHAR(60) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    connector_key VARCHAR(120),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    last_heartbeat_at TIMESTAMPTZ,
    last_synchronized_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_access_provider_connection_branch_provider
        UNIQUE (organization_id, branch_id, provider_key),
    CONSTRAINT ck_access_provider_connection_status
        CHECK (sync_status IN ('DISABLED', 'AWAITING_CONNECTOR', 'HEALTHY', 'DEGRADED'))
);

CREATE INDEX ix_access_provider_connection_org_branch
    ON access_provider_connection (organization_id, branch_id);

CREATE TABLE access_provider_benefit_mapping (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES access_provider_connection(id) ON DELETE CASCADE,
    benefit_id UUID NOT NULL REFERENCES benefit(id) ON DELETE RESTRICT,
    external_access_level_ref VARCHAR(180) NOT NULL,
    external_access_level_name VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_access_provider_benefit_mapping
        UNIQUE (connection_id, benefit_id),
    CONSTRAINT ux_access_provider_external_level
        UNIQUE (connection_id, external_access_level_ref)
);

CREATE INDEX ix_access_provider_benefit_mapping_org_benefit
    ON access_provider_benefit_mapping (organization_id, benefit_id);

CREATE TABLE access_provider_client_link (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES access_provider_connection(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    external_person_ref VARCHAR(180) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    linked_by UUID REFERENCES user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_access_provider_client_link
        UNIQUE (connection_id, client_id),
    CONSTRAINT ux_access_provider_external_person
        UNIQUE (connection_id, external_person_ref)
);

CREATE INDEX ix_access_provider_client_link_org_client
    ON access_provider_client_link (organization_id, client_id);

CREATE TABLE access_provider_sync_state (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES access_provider_connection(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    desired_access_level_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    action VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    last_synchronized_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_access_provider_sync_state
        UNIQUE (connection_id, client_id),
    CONSTRAINT ck_access_provider_sync_action
        CHECK (action IN ('UPSERT', 'REVOKE', 'RECONCILE')),
    CONSTRAINT ck_access_provider_sync_status
        CHECK (status IN ('PENDING', 'BLOCKED', 'SYNCED', 'FAILED'))
);

CREATE INDEX ix_access_provider_sync_state_pending
    ON access_provider_sync_state (connection_id, status, requested_at)
    WHERE status IN ('PENDING', 'FAILED');
