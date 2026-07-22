ALTER TABLE coach
    ADD COLUMN IF NOT EXISTS user_account_id UUID NULL;

ALTER TABLE coach
    DROP CONSTRAINT IF EXISTS fk_coach_user_account;

ALTER TABLE coach
    ADD CONSTRAINT fk_coach_user_account
        FOREIGN KEY (user_account_id)
        REFERENCES user_account(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_coach_org_branch_user
    ON coach (organization_id, branch_id, user_account_id)
    WHERE user_account_id IS NOT NULL;
