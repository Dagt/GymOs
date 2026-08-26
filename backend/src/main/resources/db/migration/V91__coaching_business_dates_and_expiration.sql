-- assigned_on is generated at creation, never supplied by the user. Older servers
-- used LocalDate.now() in UTC while reports used America/Mexico_City. Repair only
-- rows matching that exact UTC-creation-day defect; do not alter chosen periods.
UPDATE workout_assignment
SET assigned_on = (created_at AT TIME ZONE 'America/Mexico_City')::date,
    updated_at = now(), version = version + 1
WHERE assigned_on = (created_at AT TIME ZONE 'UTC')::date
  AND assigned_on <> (created_at AT TIME ZONE 'America/Mexico_City')::date;

CREATE INDEX ix_workout_assignment_due_completion ON workout_assignment (ends_on, id)
    WHERE status = 'ACTIVE' AND ends_on IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_workout_assignment_client_period ON workout_assignment
    (organization_id, branch_id, client_id, starts_on, ends_on)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
