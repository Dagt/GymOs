ALTER TABLE sale
    ADD COLUMN idempotency_key VARCHAR(160),
    ADD COLUMN business_context VARCHAR(80),
    ADD COLUMN context_membership_id UUID,
    ADD COLUMN context_target_plan_id UUID,
    ADD COLUMN payment_completed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX ux_sale_org_idempotency_key
    ON sale (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE payment
    ADD COLUMN idempotency_key VARCHAR(160);

CREATE UNIQUE INDEX ux_payment_org_idempotency_key
    ON payment (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE processed_domain_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_key VARCHAR(200) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_processed_domain_event_org_key
    ON processed_domain_event (organization_id, event_key);

ALTER TABLE self_service_session
    ADD COLUMN session_token_hash VARCHAR(128),
    ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN blocked_until TIMESTAMPTZ,
    ADD COLUMN last_request_at TIMESTAMPTZ;

CREATE UNIQUE INDEX ux_self_service_session_token_hash
    ON self_service_session (session_token_hash)
    WHERE session_token_hash IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE membership
    ADD COLUMN source_membership_id UUID,
    ADD COLUMN source_payment_event_key VARCHAR(200);

CREATE UNIQUE INDEX ux_membership_org_payment_event_key
    ON membership (organization_id, source_payment_event_key)
    WHERE source_payment_event_key IS NOT NULL AND deleted_at IS NULL;
