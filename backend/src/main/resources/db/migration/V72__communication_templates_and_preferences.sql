CREATE TABLE communication_template (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID,
    code VARCHAR(120) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'es-MX',
    subject VARCHAR(240),
    body TEXT NOT NULL,
    allowed_variables_json TEXT NOT NULL DEFAULT '[]',
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT communication_template_channel_ck CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT communication_template_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT communication_template_uq UNIQUE (organization_id, branch_id, code, channel, locale, version)
);

CREATE INDEX communication_template_lookup_idx
    ON communication_template (organization_id, branch_id, code, channel, locale, version DESC)
    WHERE status = 'ACTIVE';

CREATE TABLE client_communication_preference (
    organization_id UUID NOT NULL,
    client_id UUID NOT NULL,
    email_operational_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_operational_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_marketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_marketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    consent_version VARCHAR(64),
    consented_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, client_id)
);

CREATE TABLE communication_delivery_event (
    id UUID PRIMARY KEY,
    outbound_message_id UUID NOT NULL REFERENCES outbound_message(id),
    provider VARCHAR(80),
    provider_event_id VARCHAR(240),
    event_type VARCHAR(48) NOT NULL,
    payload_hash VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT communication_delivery_event_uq UNIQUE (provider, provider_event_id)
);

CREATE INDEX communication_delivery_event_message_idx
    ON communication_delivery_event (outbound_message_id, occurred_at);
