CREATE TABLE organization (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(180) NOT NULL,
    trade_name VARCHAR(120) NOT NULL,
    tax_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    contact_email VARCHAR(180),
    contact_phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_organization_tax_id ON organization (tax_id) WHERE tax_id IS NOT NULL;
CREATE INDEX ix_organization_trade_name ON organization (LOWER(trade_name));

CREATE TABLE branch (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    name VARCHAR(120) NOT NULL,
    code VARCHAR(32) NOT NULL,
    address_json JSONB,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_branch_org_code ON branch (organization_id, code);
CREATE INDEX ix_branch_org_status ON branch (organization_id, status);

CREATE TABLE person (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    birth_date DATE,
    gender VARCHAR(32),
    phone VARCHAR(32),
    email VARCHAR(180),
    photo_reference VARCHAR(512),
    emergency_contact_json JSONB,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_person_org_email ON person (organization_id, LOWER(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX ux_person_org_phone ON person (organization_id, phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_person_org_last_name ON person (organization_id, last_name);

CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    person_id UUID NOT NULL REFERENCES person(id),
    username VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_login_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_user_org_username ON user_account (organization_id, LOWER(username));

CREATE TABLE role (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    module VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_permission_code ON permission (code);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES user_account(id),
    role_id UUID NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
    role_id UUID NOT NULL REFERENCES role(id),
    permission_id UUID NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_branch_access (
    user_id UUID NOT NULL REFERENCES user_account(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    access_scope VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE client (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    person_id UUID NOT NULL REFERENCES person(id),
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_client_org_person ON client (organization_id, person_id);

CREATE TABLE self_service_terminal (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    name VARCHAR(120) NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    allowed_actions_json JSONB,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_terminal_org_device ON self_service_terminal (organization_id, device_code);
CREATE INDEX ix_terminal_org_branch ON self_service_terminal (organization_id, branch_id);

CREATE TABLE self_service_session (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    terminal_id UUID NOT NULL REFERENCES self_service_terminal(id),
    client_id UUID REFERENCES client(id),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    verification_method VARCHAR(64),
    origin_ip VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_self_service_session_terminal_status ON self_service_session (terminal_id, status);
CREATE INDEX ix_self_service_session_client_started ON self_service_session (client_id, started_at);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    actor_user_id UUID,
    actor_role VARCHAR(120),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id UUID,
    before_json JSONB,
    after_json JSONB,
    reason TEXT,
    origin VARCHAR(80),
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_audit_org_occurred ON audit_log (organization_id, occurred_at);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_correlation ON audit_log (correlation_id);
