CREATE TABLE platform_customer_account (
    organization_id UUID PRIMARY KEY REFERENCES organization(id) ON DELETE CASCADE,
    lifecycle_status VARCHAR(24) NOT NULL DEFAULT 'ONBOARDING',
    account_owner VARCHAR(120),
    billing_email VARCHAR(254),
    billing_phone VARCHAR(32),
    contract_started_on DATE,
    renewal_on DATE,
    payment_terms_days SMALLINT NOT NULL DEFAULT 0,
    next_follow_up_on DATE,
    notes VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    CONSTRAINT ck_platform_customer_account_lifecycle CHECK (
        lifecycle_status IN ('ONBOARDING', 'ACTIVE', 'AT_RISK', 'OFFBOARDING', 'FORMER')
    ),
    CONSTRAINT ck_platform_customer_account_payment_terms CHECK (
        payment_terms_days BETWEEN 0 AND 90
    ),
    CONSTRAINT ck_platform_customer_account_renewal CHECK (
        renewal_on IS NULL OR contract_started_on IS NULL OR renewal_on >= contract_started_on
    )
);

CREATE INDEX ix_platform_customer_account_lifecycle
    ON platform_customer_account (lifecycle_status);

CREATE INDEX ix_platform_customer_account_follow_up
    ON platform_customer_account (next_follow_up_on)
    WHERE next_follow_up_on IS NOT NULL
      AND lifecycle_status NOT IN ('FORMER');

ALTER TABLE platform_branch_billing
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE platform_billing_payment
    ADD COLUMN idempotency_key VARCHAR(120);

UPDATE platform_billing_payment
SET idempotency_key = 'legacy-' || id::text
WHERE idempotency_key IS NULL;

ALTER TABLE platform_billing_payment
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX ux_platform_billing_payment_idempotency
    ON platform_billing_payment (branch_id, idempotency_key);
