CREATE TABLE payment_provider_connection (
    organization_id UUID NOT NULL REFERENCES organization(id),
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(180),
    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at TIMESTAMPTZ,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_error TEXT,
    connected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, provider)
);

CREATE TABLE payment_provider_oauth_request (
    state_hash VARCHAR(128) PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    provider VARCHAR(32) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES user_account(id),
    code_verifier_encrypted TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_payment_provider_oauth_expiry
    ON payment_provider_oauth_request (provider, expires_at);

CREATE TABLE payment_provider_operation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    provider VARCHAR(32) NOT NULL,
    operation_type VARCHAR(48) NOT NULL,
    sale_id UUID,
    payment_id UUID,
    client_id UUID,
    external_operation_id VARCHAR(200),
    idempotency_key VARCHAR(180) NOT NULL,
    status VARCHAR(48) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    checkout_url TEXT,
    provider_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_payment_provider_operation_idempotency
    ON payment_provider_operation (organization_id, provider, idempotency_key);

CREATE UNIQUE INDEX ux_payment_provider_external_operation
    ON payment_provider_operation (provider, external_operation_id)
    WHERE external_operation_id IS NOT NULL;

CREATE INDEX ix_payment_provider_operation_sale
    ON payment_provider_operation (organization_id, sale_id, provider);

CREATE TABLE payment_provider_webhook_event (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    provider VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(120),
    payload_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_payment_provider_webhook_event
    ON payment_provider_webhook_event (provider, external_event_id);
