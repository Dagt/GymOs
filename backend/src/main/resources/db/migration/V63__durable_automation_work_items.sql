CREATE TABLE automation_work_item (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID,
    rule_id UUID NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    trigger_reference VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    scheduled_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    effect_reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_automation_work_item_rule
        FOREIGN KEY (rule_id) REFERENCES automation_rule(id),
    CONSTRAINT ck_automation_work_item_status
        CHECK (status IN ('PENDING', 'RUNNING', 'RETRY_SCHEDULED', 'SUCCEEDED', 'DEAD_LETTER', 'CANCELLED')),
    CONSTRAINT ck_automation_work_item_attempts
        CHECK (attempt_count >= 0 AND max_attempts >= 1)
);

CREATE UNIQUE INDEX uk_automation_work_item_idempotency
    ON automation_work_item (organization_id, idempotency_key);

CREATE INDEX ix_automation_work_item_due
    ON automation_work_item (next_attempt_at, status)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE INDEX ix_automation_work_item_rule
    ON automation_work_item (organization_id, branch_id, rule_id, created_at DESC);
