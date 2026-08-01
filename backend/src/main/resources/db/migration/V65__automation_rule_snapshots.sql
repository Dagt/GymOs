CREATE TABLE automation_rule_snapshot (
    work_item_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID,
    rule_id UUID NOT NULL,
    rule_version BIGINT NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    trigger_type VARCHAR(80) NOT NULL,
    condition_json JSONB NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    action_config_json JSONB NOT NULL,
    retry_limit INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_automation_rule_snapshot_work_item
        FOREIGN KEY (work_item_id) REFERENCES automation_work_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_rule_snapshot_rule
        FOREIGN KEY (rule_id) REFERENCES automation_rule(id)
);

CREATE INDEX ix_automation_rule_snapshot_rule
    ON automation_rule_snapshot (organization_id, branch_id, rule_id, rule_version);
