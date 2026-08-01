CREATE TABLE self_service_quote (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    session_id UUID NOT NULL,
    client_id UUID NOT NULL,
    membership_id UUID,
    operation_type VARCHAR(40) NOT NULL,
    plan_id UUID NOT NULL,
    plan_name VARCHAR(200) NOT NULL,
    list_price_cents BIGINT NOT NULL,
    discount_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    applied_benefits_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_self_service_quote_session
        FOREIGN KEY (session_id) REFERENCES self_service_session(id),
    CONSTRAINT ck_self_service_quote_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_self_service_quote_amounts
        CHECK (list_price_cents >= 0 AND discount_cents >= 0 AND total_cents >= 0)
);

CREATE INDEX ix_self_service_quote_session
    ON self_service_quote (session_id, status, expires_at DESC);

CREATE TABLE self_service_payment_intent (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    session_id UUID NOT NULL,
    client_id UUID NOT NULL,
    quote_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_intent_id VARCHAR(240),
    idempotency_key VARCHAR(160) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(40) NOT NULL,
    checkout_url VARCHAR(1000),
    client_secret VARCHAR(1000),
    expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_self_service_payment_intent_session
        FOREIGN KEY (session_id) REFERENCES self_service_session(id),
    CONSTRAINT fk_self_service_payment_intent_quote
        FOREIGN KEY (quote_id) REFERENCES self_service_quote(id),
    CONSTRAINT ck_self_service_payment_intent_status
        CHECK (status IN ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT uk_self_service_payment_intent_idempotency
        UNIQUE (organization_id, idempotency_key)
);

CREATE INDEX ix_self_service_payment_intent_provider
    ON self_service_payment_intent (provider, provider_intent_id)
    WHERE provider_intent_id IS NOT NULL;
