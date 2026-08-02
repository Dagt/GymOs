CREATE TABLE self_service_autopay_mandate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID NOT NULL REFERENCES client(id),
    membership_id UUID REFERENCES membership(id),
    provider VARCHAR(80),
    provider_customer_id VARCHAR(180),
    provider_payment_method_id VARCHAR(180),
    status VARCHAR(32) NOT NULL,
    consent_version VARCHAR(40) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    next_charge_at TIMESTAMPTZ,
    last_charge_at TIMESTAMPTZ,
    last_charge_status VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_self_service_autopay_status CHECK (
        status IN ('PENDING_METHOD', 'ACTIVE', 'PAUSED', 'REVOKED', 'FAILED')
    )
);

CREATE UNIQUE INDEX uq_self_service_autopay_active_membership
    ON self_service_autopay_mandate (organization_id, membership_id)
    WHERE membership_id IS NOT NULL AND status IN ('PENDING_METHOD', 'ACTIVE', 'PAUSED');

CREATE INDEX ix_self_service_autopay_client
    ON self_service_autopay_mandate (organization_id, branch_id, client_id, created_at DESC);

CREATE TABLE self_service_autopay_event (
    id UUID PRIMARY KEY,
    mandate_id UUID NOT NULL REFERENCES self_service_autopay_mandate(id),
    event_type VARCHAR(60) NOT NULL,
    event_reference VARCHAR(180),
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_self_service_autopay_event_mandate
    ON self_service_autopay_event (mandate_id, occurred_at DESC);
