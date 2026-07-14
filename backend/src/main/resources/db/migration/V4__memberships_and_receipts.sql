CREATE TABLE membership (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID NOT NULL REFERENCES client(id),
    plan_id UUID NOT NULL REFERENCES membership_plan(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    status VARCHAR(32) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    grace_ends_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_membership_org_client_status ON membership (organization_id, client_id, status);
CREATE INDEX ix_membership_org_sale ON membership (organization_id, sale_id);
CREATE UNIQUE INDEX ux_membership_org_sale_plan ON membership (organization_id, sale_id, plan_id) WHERE deleted_at IS NULL;
