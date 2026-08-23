CREATE TABLE platform_billing_payment (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branch(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    payment_type VARCHAR(24) NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    paid_on DATE NOT NULL,
    reference VARCHAR(160),
    notes VARCHAR(1000),
    recorded_by UUID NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_platform_billing_payment_type CHECK (
        payment_type IN ('SUBSCRIPTION', 'IMPLEMENTATION', 'OTHER')
    ),
    CONSTRAINT ck_platform_billing_payment_method CHECK (
        payment_method IN ('TRANSFER', 'CASH', 'CARD', 'STRIPE', 'OTHER')
    ),
    CONSTRAINT ck_platform_billing_payment_amount CHECK (amount > 0)
);

CREATE INDEX ix_platform_billing_payment_branch_paid_on
    ON platform_billing_payment (branch_id, paid_on DESC, created_at DESC);

CREATE INDEX ix_platform_billing_payment_organization_paid_on
    ON platform_billing_payment (organization_id, paid_on DESC, created_at DESC);
