CREATE TABLE automation_rule_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID,
    rule_id UUID NOT NULL,
    definition_version BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    trigger_type VARCHAR(80) NOT NULL,
    condition_json JSONB NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    action_config_json JSONB NOT NULL,
    retry_limit INTEGER NOT NULL,
    schedule_cron VARCHAR(160),
    next_run_at TIMESTAMPTZ,
    change_type VARCHAR(40) NOT NULL,
    changed_by UUID,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_automation_rule_revision_rule
        FOREIGN KEY (rule_id) REFERENCES automation_rule(id),
    CONSTRAINT uk_automation_rule_revision_version
        UNIQUE (rule_id, definition_version)
);

CREATE INDEX ix_automation_rule_revision_lookup
    ON automation_rule_revision (organization_id, branch_id, rule_id, definition_version DESC);

ALTER TABLE automation_rule
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

INSERT INTO automation_rule_revision (
    id, organization_id, branch_id, rule_id, definition_version,
    name, trigger_type, condition_json, action_type, action_config_json,
    retry_limit, schedule_cron, next_run_at, change_type, changed_by, changed_at
)
SELECT gen_random_uuid(), organization_id, branch_id, id, definition_version,
       name, trigger_type, condition_json, action_type, action_config_json,
       retry_limit, schedule_cron, next_run_at, 'BASELINE', created_by, created_at
FROM automation_rule
ON CONFLICT (rule_id, definition_version) DO NOTHING;
