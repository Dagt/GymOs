ALTER TABLE client
    ADD COLUMN IF NOT EXISTS home_branch_id UUID REFERENCES branch(id);

UPDATE client c
SET home_branch_id = COALESCE(
    (
        SELECT m.branch_id
        FROM membership m
        WHERE m.organization_id = c.organization_id
          AND m.client_id = c.id
          AND m.deleted_at IS NULL
        ORDER BY m.activated_at ASC NULLS LAST, m.created_at ASC
        LIMIT 1
    ),
    (
        SELECT b.id
        FROM branch b
        WHERE b.organization_id = c.organization_id
          AND b.deleted_at IS NULL
        ORDER BY CASE WHEN b.status = 'ACTIVE' THEN 0 ELSE 1 END, b.created_at ASC
        LIMIT 1
    )
)
WHERE c.home_branch_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_client_org_home_branch_status
    ON client (organization_id, home_branch_id, status)
    WHERE deleted_at IS NULL;

DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'Gerente'
  AND p.code IN ('organization.update', 'branch.create', 'branch.update');
