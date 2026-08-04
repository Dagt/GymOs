ALTER TABLE membership
    ADD COLUMN expired_event_published_at TIMESTAMPTZ;

ALTER TABLE automation_execution
    ADD COLUMN occurrence_reference VARCHAR(200);

UPDATE automation_execution
SET occurrence_reference = trigger_reference
WHERE occurrence_reference IS NULL;

CREATE INDEX ix_automation_execution_rule_occurrence
    ON automation_execution (organization_id, rule_id, occurrence_reference);

-- Historical expirations predate the lifecycle event contract and must not
-- generate a notification storm when this migration is deployed.
UPDATE membership
SET expired_event_published_at = COALESCE(expired_event_published_at, NOW())
WHERE status = 'EXPIRED';

CREATE INDEX ix_membership_expired_event_pending
    ON membership (grace_ends_on, id)
    WHERE status = 'EXPIRED'
      AND expired_event_published_at IS NULL
      AND deleted_at IS NULL;

CREATE TABLE membership_lifecycle_occurrence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    membership_id UUID NOT NULL REFERENCES membership(id),
    event_type VARCHAR(40) NOT NULL,
    effective_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_membership_lifecycle_event_type CHECK (
        event_type IN ('EXPIRING')
    )
);

CREATE UNIQUE INDEX ux_membership_lifecycle_occurrence
    ON membership_lifecycle_occurrence (
        organization_id, membership_id, event_type, effective_on
    );

CREATE INDEX ix_membership_lifecycle_occurrence_created
    ON membership_lifecycle_occurrence (created_at);

-- Repair the legacy demo rule so it complies with the typed notification
-- contract now that MEMBERSHIP_EXPIRING has a real producer.
UPDATE automation_rule
SET action_config_json = jsonb_set(
        action_config_json,
        '{body}',
        to_jsonb(COALESCE(NULLIF(action_config_json ->> 'subject', ''), name)),
        true
    ),
    updated_at = NOW(),
    version = version + 1
WHERE trigger_type IN ('MEMBERSHIP_EXPIRING', 'MEMBERSHIP_EXPIRED')
  AND COALESCE(action_config_json ->> 'body', '') = '';
