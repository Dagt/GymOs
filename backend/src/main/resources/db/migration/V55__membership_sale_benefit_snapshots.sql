CREATE TABLE membership_sale_benefit (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    sale_id UUID NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    benefit_id UUID NOT NULL,
    benefit_code_snapshot VARCHAR(80) NOT NULL,
    benefit_name_snapshot VARCHAR(160) NOT NULL,
    discount_cents BIGINT NOT NULL,
    application_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_membership_sale_benefit_definition
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id),
    CONSTRAINT ck_membership_sale_benefit_discount
        CHECK (discount_cents >= 0),
    CONSTRAINT ck_membership_sale_benefit_order
        CHECK (application_order >= 0),
    CONSTRAINT ux_membership_sale_benefit
        UNIQUE (organization_id, sale_id, benefit_id)
);

CREATE INDEX ix_membership_sale_benefit_sale
    ON membership_sale_benefit (organization_id, sale_id, application_order);
