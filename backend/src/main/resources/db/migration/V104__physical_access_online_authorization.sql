-- Distinguish devices whose provider already decides access from devices that
-- must ask GymFort before the connector actuates the physical relay.
ALTER TABLE access_provider_device_link
    ADD COLUMN authorization_mode VARCHAR(32) NOT NULL DEFAULT 'PROVIDER_MANAGED',
    ADD CONSTRAINT ck_access_provider_device_link_authorization_mode
        CHECK (authorization_mode IN ('PROVIDER_MANAGED', 'GYMFORT_ONLINE'));

ALTER TABLE access_provider_connection
    ADD COLUMN connector_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_access_provider_connection_capabilities_v104
        CHECK (jsonb_typeof(connector_capabilities) = 'array');

-- A composite key lets every reference to an access decision carry the same
-- tenant and branch boundary. The primary key already guarantees global
-- uniqueness; this additional key exists specifically for tenant-safe FKs.
ALTER TABLE access_log
    ADD CONSTRAINT ux_access_log_tenant_identity_v104
        UNIQUE (id, organization_id, branch_id);

-- Durable, tenant-scoped idempotency and actuation audit for online decisions.
-- The access decision and visit-package consumption remain in access_log and
-- visit_package_consumption; this table records the provider event and relay
-- outcome without creating a second balance movement.
CREATE TABLE access_physical_authorization (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    access_device_id UUID NOT NULL,
    client_id UUID,
    external_event_id VARCHAR(180) NOT NULL,
    external_person_ref VARCHAR(180),
    external_device_ref VARCHAR(180) NOT NULL,
    provider_decision VARCHAR(24),
    authorization_mode VARCHAR(32) NOT NULL,
    decision VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    access_log_id UUID,
    actuation_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    external_operation_ref VARCHAR(180),
    last_error VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_physical_authorization_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_physical_authorization_device
        FOREIGN KEY (access_device_id, organization_id, branch_id)
        REFERENCES access_device(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_physical_authorization_client
        FOREIGN KEY (client_id, organization_id)
        REFERENCES client(id, organization_id)
        ON DELETE SET NULL (client_id),
    CONSTRAINT fk_access_physical_authorization_log
        FOREIGN KEY (access_log_id, organization_id, branch_id)
        REFERENCES access_log(id, organization_id, branch_id)
        ON DELETE SET NULL (access_log_id),
    CONSTRAINT ux_access_physical_authorization_event
        UNIQUE (connection_id, external_event_id),
    CONSTRAINT ck_access_physical_authorization_mode
        CHECK (authorization_mode IN ('PROVIDER_MANAGED', 'GYMFORT_ONLINE')),
    CONSTRAINT ck_access_physical_authorization_provider_decision
        CHECK (provider_decision IS NULL OR provider_decision IN ('GRANTED', 'DENIED')),
    CONSTRAINT ck_access_physical_authorization_decision
        CHECK (decision IN ('PENDING', 'GRANTED', 'DENIED', 'FAILED')),
    CONSTRAINT ck_access_physical_authorization_actuation
        CHECK (actuation_status IN ('NOT_REQUIRED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNCERTAIN')),
    CONSTRAINT ck_access_physical_authorization_log_decision
        CHECK ((decision = 'PENDING' AND access_log_id IS NULL)
            OR (decision <> 'PENDING' AND access_log_id IS NOT NULL)),
    CONSTRAINT ck_access_physical_authorization_actuation_consistency
        CHECK (actuation_status = 'NOT_REQUIRED'
            OR (authorization_mode = 'GYMFORT_ONLINE' AND decision = 'GRANTED'))
);

CREATE INDEX ix_access_physical_authorization_tenant
    ON access_physical_authorization (
        organization_id, branch_id, connection_id, occurred_at DESC
    );

CREATE INDEX ix_access_physical_authorization_pending_actuation
    ON access_physical_authorization (connection_id, actuation_status, updated_at)
    WHERE actuation_status IN ('PENDING', 'UNCERTAIN', 'FAILED');

-- Provider resource discovery is intentionally separate from configured access
-- devices. An operator must explicitly link a discovered resource before it can
-- authorize or actuate anything.
CREATE TABLE access_provider_device_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    external_device_ref VARCHAR(180) NOT NULL,
    external_device_name VARCHAR(180),
    external_device_type VARCHAR(80),
    status VARCHAR(24) NOT NULL DEFAULT 'UNLINKED',
    linked_access_device_id UUID,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_provider_device_candidate_connection
        FOREIGN KEY (connection_id, organization_id, branch_id)
        REFERENCES access_provider_connection(id, organization_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_provider_device_candidate_device
        FOREIGN KEY (linked_access_device_id, organization_id, branch_id)
        REFERENCES access_device(id, organization_id, branch_id)
        ON DELETE SET NULL (linked_access_device_id),
    CONSTRAINT ux_access_provider_device_candidate_external
        UNIQUE (connection_id, external_device_ref),
    CONSTRAINT ck_access_provider_device_candidate_status
        CHECK (status IN ('UNLINKED', 'LINKED', 'IGNORED')),
    CONSTRAINT ck_access_provider_device_candidate_link
        CHECK ((status = 'LINKED' AND linked_access_device_id IS NOT NULL)
            OR (status IN ('UNLINKED', 'IGNORED') AND linked_access_device_id IS NULL)),
    CONSTRAINT ck_access_provider_device_candidate_metadata
        CHECK (jsonb_typeof(metadata_json) = 'object')
);

CREATE INDEX ix_access_provider_device_candidate_status
    ON access_provider_device_candidate (
        organization_id, branch_id, connection_id, status, last_seen_at DESC
    );
