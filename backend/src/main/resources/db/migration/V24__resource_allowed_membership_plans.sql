CREATE TABLE IF NOT EXISTS resource_allowed_membership_plan (
    resource_id UUID NOT NULL REFERENCES resource(id),
    plan_id UUID NOT NULL REFERENCES membership_plan(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (resource_id, plan_id)
);

CREATE INDEX IF NOT EXISTS ix_resource_allowed_plan_org_resource
    ON resource_allowed_membership_plan (organization_id, resource_id);

CREATE INDEX IF NOT EXISTS ix_resource_allowed_plan_org_plan
    ON resource_allowed_membership_plan (organization_id, plan_id);
