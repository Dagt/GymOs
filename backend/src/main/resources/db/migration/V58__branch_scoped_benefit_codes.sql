-- Benefit definitions are operationally scoped to a branch. Keeping the code
-- unique per organization made a branch-local benefit impossible to reproduce
-- in another branch with the same business code.
--
-- Older UI versions omitted branch_id even though the audit event did retain
-- the selected branch. Repair those definitions before changing uniqueness.
WITH created_scope AS (
    SELECT DISTINCT ON (organization_id, entity_id)
        organization_id,
        entity_id AS benefit_id,
        branch_id
    FROM audit_log
    WHERE action = 'BENEFIT_CREATED'
      AND entity_type = 'BENEFIT'
      AND entity_id IS NOT NULL
      AND branch_id IS NOT NULL
    ORDER BY organization_id, entity_id, occurred_at ASC
)
UPDATE benefit b
SET branch_id = created_scope.branch_id,
    updated_at = now(),
    version = b.version + 1
FROM created_scope
WHERE b.organization_id = created_scope.organization_id
  AND b.id = created_scope.benefit_id
  AND b.branch_id IS NULL;

DROP INDEX IF EXISTS ux_benefit_org_code;

CREATE UNIQUE INDEX ux_benefit_org_branch_code
    ON benefit (organization_id, branch_id, code)
    WHERE branch_id IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX ux_benefit_org_global_code
    ON benefit (organization_id, code)
    WHERE branch_id IS NULL AND deleted_at IS NULL;
