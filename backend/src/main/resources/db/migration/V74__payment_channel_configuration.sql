CREATE TABLE payment_channel_configuration (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    mercado_pago_point_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mercado_pago_access_token_ref VARCHAR(240),
    mercado_pago_terminal_id VARCHAR(160),
    external_terminal_manual_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    stripe_kiosk_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_secret_key_ref VARCHAR(240),
    stripe_webhook_secret_ref VARCHAR(240),
    stripe_autopay_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    environment VARCHAR(16) NOT NULL DEFAULT 'SANDBOX',
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payment_channel_environment_ck CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT payment_channel_configuration_uq UNIQUE (organization_id, branch_id)
);

CREATE TABLE card_present_payment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    mode VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'MXN',
    idempotency_key VARCHAR(180) NOT NULL,
    external_order_id VARCHAR(240),
    terminal_id VARCHAR(160),
    manual_reference VARCHAR(160),
    authorization_code VARCHAR(120),
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT card_present_payment_mode_ck CHECK (mode IN ('MERCADO_PAGO_POINT', 'EXTERNAL_TERMINAL_MANUAL')),
    CONSTRAINT card_present_payment_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'FAILED')),
    CONSTRAINT card_present_payment_idempotency_uq UNIQUE (organization_id, idempotency_key)
);

CREATE INDEX card_present_payment_sale_idx
    ON card_present_payment (organization_id, branch_id, sale_id, created_at DESC);
CREATE INDEX card_present_payment_external_idx
    ON card_present_payment (external_order_id)
    WHERE external_order_id IS NOT NULL;
