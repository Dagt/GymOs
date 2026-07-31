-- A tenant owner must be able to read the organization it can administer.
-- Keep this repair explicit for roles created or customized after the earlier
-- role-template migrations were executed.
INSERT INTO permission (
    id, code, description, module, created_at, updated_at, version
)
VALUES (
    '8a000000-0000-0000-0000-000000000014',
    'organization.read',
    'Permiso organization.read',
    'organization',
    now(),
    now(),
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT owner_role.id, permission.id
FROM role owner_role
JOIN permission ON permission.code IN ('organization.read', 'organization.update')
WHERE owner_role.deleted_at IS NULL
  AND (
      LOWER(owner_role.name) LIKE '%dueño%'
      OR LOWER(owner_role.name) LIKE '%dueno%'
      OR LOWER(owner_role.name) LIKE '%owner%'
  )
ON CONFLICT DO NOTHING;

ALTER TABLE organization
    ADD COLUMN IF NOT EXISTS logo_object_key VARCHAR(500);
