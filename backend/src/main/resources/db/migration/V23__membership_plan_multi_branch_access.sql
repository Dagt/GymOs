ALTER TABLE membership_plan
    ADD COLUMN IF NOT EXISTS multi_branch_access BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS ix_membership_plan_org_multi_branch
    ON membership_plan (organization_id, multi_branch_access, status)
    WHERE deleted_at IS NULL;
