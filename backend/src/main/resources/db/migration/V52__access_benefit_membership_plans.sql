CREATE UNIQUE INDEX ux_benefit_id_organization
    ON benefit (id, organization_id);

CREATE UNIQUE INDEX ux_membership_plan_id_organization
    ON membership_plan (id, organization_id);

CREATE TABLE benefit_membership_plan (
    benefit_id UUID NOT NULL,
    membership_plan_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    PRIMARY KEY (benefit_id, membership_plan_id),
    CONSTRAINT fk_benefit_membership_plan_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_benefit_membership_plan_plan
        FOREIGN KEY (membership_plan_id, organization_id)
        REFERENCES membership_plan(id, organization_id)
        ON DELETE CASCADE
);

CREATE INDEX ix_benefit_membership_plan_org_plan
    ON benefit_membership_plan (organization_id, membership_plan_id);
