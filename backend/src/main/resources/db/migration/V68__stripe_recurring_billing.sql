CREATE TABLE payment_customer (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    client_id UUID NOT NULL REFERENCES client(id),
    provider VARCHAR(32) NOT NULL,
    external_customer_id VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_payment_customer_client UNIQUE (organization_id, client_id, provider)
);

CREATE UNIQUE INDEX ux_payment_customer_external
    ON payment_customer (provider, external_customer_id)
    WHERE external_customer_id IS NOT NULL;

CREATE TABLE stored_payment_method (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    client_id UUID NOT NULL REFERENCES client(id),
    payment_customer_id UUID NOT NULL REFERENCES payment_customer(id),
    provider VARCHAR(32) NOT NULL,
    external_payment_method_id VARCHAR(200) NOT NULL,
    method_type VARCHAR(32) NOT NULL,
    brand VARCHAR(40),
    last_four CHAR(4),
    expires_month INTEGER,
    expires_year INTEGER,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_stored_payment_method_status CHECK (status IN ('ACTIVE', 'DETACHED', 'REPLACED')),
    CONSTRAINT ck_stored_payment_method_expiry CHECK (
        (expires_month IS NULL OR expires_month BETWEEN 1 AND 12)
        AND (expires_year IS NULL OR expires_year >= 2020)
    )
);

CREATE UNIQUE INDEX ux_stored_payment_method_external
    ON stored_payment_method (organization_id, provider, external_payment_method_id);

CREATE TABLE recurring_payment_mandate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID NOT NULL REFERENCES client(id),
    membership_id UUID NOT NULL REFERENCES membership(id),
    plan_id UUID NOT NULL REFERENCES membership_plan(id),
    payment_customer_id UUID NOT NULL REFERENCES payment_customer(id),
    stored_payment_method_id UUID REFERENCES stored_payment_method(id),
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    terms_version VARCHAR(40) NOT NULL,
    consent_text TEXT NOT NULL,
    amount_rule VARCHAR(80) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    consent_origin VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    next_charge_on DATE NOT NULL,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_recurring_payment_mandate_status CHECK (
        status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED', 'REVOKED', 'REPLACED')
    ),
    CONSTRAINT ck_recurring_payment_mandate_failures CHECK (consecutive_failures >= 0),
    CONSTRAINT ux_recurring_payment_mandate_key UNIQUE (organization_id, idempotency_key)
);

CREATE UNIQUE INDEX ux_recurring_payment_mandate_current
    ON recurring_payment_mandate (organization_id, membership_id)
    WHERE status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED');

CREATE INDEX ix_recurring_payment_mandate_due
    ON recurring_payment_mandate (status, next_charge_on)
    WHERE status IN ('ACTIVE', 'PAST_DUE');

CREATE TABLE recurring_charge_occurrence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    mandate_id UUID NOT NULL REFERENCES recurring_payment_mandate(id),
    membership_id UUID NOT NULL REFERENCES membership(id),
    due_on DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    sale_id UUID,
    payment_id UUID,
    provider_operation_id UUID,
    amount_cents BIGINT,
    currency CHAR(3),
    attempt_number INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_recurring_charge_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'AWAITING_PROVIDER', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_recurring_charge_attempt CHECK (attempt_number >= 0),
    CONSTRAINT ux_recurring_charge_occurrence UNIQUE (mandate_id, due_on)
);

CREATE INDEX ix_recurring_charge_due
    ON recurring_charge_occurrence (status, next_attempt_at, locked_until)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED');

CREATE UNIQUE INDEX ux_recurring_charge_sale
    ON recurring_charge_occurrence (organization_id, sale_id)
    WHERE sale_id IS NOT NULL;

ALTER TABLE payment_provider_operation
    ADD COLUMN mandate_id UUID REFERENCES recurring_payment_mandate(id),
    ADD COLUMN recurring_charge_id UUID REFERENCES recurring_charge_occurrence(id);

CREATE INDEX ix_payment_provider_operation_mandate
    ON payment_provider_operation (organization_id, mandate_id, created_at);

CREATE INDEX ix_payment_provider_operation_recurring_charge
    ON payment_provider_operation (organization_id, recurring_charge_id, created_at);

-- The owner still controls availability with stripe_recurring_enabled. The
-- logical browser profile only declares that this UI is capable of offering it.
UPDATE self_service_terminal
SET allowed_actions_json = allowed_actions_json || '{"autopay":true}'::jsonb,
    updated_at = NOW(),
    version = version + 1
WHERE deleted_at IS NULL;
