CREATE TABLE platform_branch_billing (
    branch_id UUID PRIMARY KEY REFERENCES branch(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    subscription_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    billing_anchor_day SMALLINT NOT NULL,
    next_billing_date DATE NOT NULL,
    last_payment_date DATE,
    last_payment_amount NUMERIC(12, 2),
    plan_override VARCHAR(32),
    monthly_price_override NUMERIC(12, 2),
    implementation_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    implementation_paid BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    CONSTRAINT ck_platform_branch_billing_status CHECK (
        subscription_status IN ('ACTIVE', 'PENDING', 'PAUSED', 'CANCELLED')
    ),
    CONSTRAINT ck_platform_branch_billing_anchor_day CHECK (
        billing_anchor_day BETWEEN 1 AND 31
    ),
    CONSTRAINT ck_platform_branch_billing_plan_override CHECK (
        plan_override IS NULL OR plan_override IN ('GYMFORT_150', 'GYMFORT_500', 'GYMFORT_1000', 'GYMFORT_2000', 'ENTERPRISE')
    ),
    CONSTRAINT ck_platform_branch_billing_price_override CHECK (
        monthly_price_override IS NULL OR monthly_price_override >= 0
    ),
    CONSTRAINT ck_platform_branch_billing_implementation_fee CHECK (
        implementation_fee >= 0
    )
);

CREATE INDEX ix_platform_branch_billing_org
    ON platform_branch_billing (organization_id);

CREATE INDEX ix_platform_branch_billing_next_date
    ON platform_branch_billing (next_billing_date)
    WHERE subscription_status = 'ACTIVE';
