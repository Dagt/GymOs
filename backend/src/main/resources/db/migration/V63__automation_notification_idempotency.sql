ALTER TABLE notification_log
    ADD COLUMN idempotency_key VARCHAR(200);

CREATE UNIQUE INDEX ux_notification_org_idempotency
    ON notification_log (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE automation_execution
    ADD COLUMN idempotency_key VARCHAR(200),
    ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ;

ALTER TABLE automation_rule
    ADD COLUMN current_trigger_reference VARCHAR(200);

CREATE UNIQUE INDEX ux_automation_execution_org_rule_idempotency
    ON automation_execution (organization_id, rule_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

UPDATE automation_execution
SET started_at = occurred_at,
    finished_at = occurred_at
WHERE started_at IS NULL;
