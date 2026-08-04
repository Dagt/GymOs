CREATE TABLE automation_trigger_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    trigger_type VARCHAR(80) NOT NULL,
    trigger_reference VARCHAR(200) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 0 CHECK (attempt_number >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_automation_trigger_event_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'COMPLETED', 'DEAD_LETTER', 'DISCARDED')
    )
);

CREATE UNIQUE INDEX ux_automation_trigger_event_occurrence
    ON automation_trigger_event (organization_id, trigger_type, trigger_reference);

CREATE INDEX ix_automation_trigger_event_due
    ON automation_trigger_event (status, available_at, locked_until)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING');

ALTER TABLE automation_execution
    ALTER COLUMN trigger_reference TYPE VARCHAR(200),
    ADD COLUMN rule_version BIGINT NOT NULL DEFAULT 0 CHECK (rule_version >= 0);

-- Preserve legacy definitions for future migration, but never leave a rule
-- enabled when this release has no producer/consumer for its trigger.
UPDATE automation_rule
SET status = 'DISABLED',
    locked_until = NULL,
    current_trigger_reference = NULL,
    updated_at = now(),
    version = version + 1
WHERE status = 'ENABLED'
  AND trigger_type NOT IN (
      'MANUAL',
      'SCHEDULED',
      'PAYMENT_COMPLETED',
      'PAYMENT_PENDING_VALIDATION',
      'BOOKING_CREATED',
      'BOOKING_CONFIRMED',
      'BOOKING_CANCELLED',
      'MEMBERSHIP_EXPIRING',
      'MEMBERSHIP_EXPIRED'
  );
