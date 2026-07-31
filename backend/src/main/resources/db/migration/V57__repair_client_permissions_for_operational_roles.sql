-- client.read/client.create/client.update were referenced by role migrations
-- before any Flyway migration created them. In local mode the development seed
-- added them only after Flyway and granted them to its demo owner, leaving the
-- global manager and reception templates incomplete on a clean installation.
INSERT INTO permission (
    id, code, description, module, created_at, updated_at, version
)
VALUES
    (
        '8a000000-0000-0000-0000-000000000015',
        'client.read',
        'Permiso client.read',
        'client',
        now(),
        now(),
        0
    ),
    (
        '8a000000-0000-0000-0000-000000000016',
        'client.create',
        'Permiso client.create',
        'client',
        now(),
        now(),
        0
    ),
    (
        '8a000000-0000-0000-0000-000000000017',
        'client.update',
        'Permiso client.update',
        'client',
        now(),
        now(),
        0
    )
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT operational_role.id, permission.id
FROM role operational_role
JOIN permission ON permission.code IN ('client.read', 'client.create', 'client.update')
WHERE operational_role.deleted_at IS NULL
  AND (
      LOWER(operational_role.name) LIKE '%dueno%'
      OR LOWER(operational_role.name) LIKE '%owner%'
      OR LOWER(operational_role.name) LIKE '%gerente%'
      OR LOWER(operational_role.name) LIKE '%manager%'
      OR LOWER(operational_role.name) LIKE '%recepcion%'
      OR LOWER(operational_role.name) LIKE '%reception%'
      OR LOWER(operational_role.name) LIKE '%administrador%'
      OR LOWER(operational_role.name) LIKE '%administrator%'
  )
ON CONFLICT DO NOTHING;
