-- V42 may already have been applied in existing development databases. Keep this
-- migration idempotent so the corrected grants are applied without editing the
-- checksum of an executed Flyway migration.

INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT eligible.role_id, client_create.id
FROM permission client_create
JOIN (
    SELECT rp.role_id
    FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    WHERE p.code = 'client.manage'

    UNION

    SELECT client_read.role_id
    FROM role_permission client_read
    JOIN permission read_permission
      ON read_permission.id = client_read.permission_id
     AND read_permission.code = 'client.read'
    JOIN role_permission operational ON operational.role_id = client_read.role_id
    JOIN permission operational_permission
      ON operational_permission.id = operational.permission_id
     AND operational_permission.code = 'sale.create'

    UNION

    SELECT rp.role_id
    FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    WHERE p.code = 'platform.admin'

    UNION

    SELECT r.id
    FROM role r
    WHERE r.deleted_at IS NULL
      AND (
          LOWER(r.name) LIKE '%dueño%'
          OR LOWER(r.name) LIKE '%dueno%'
          OR LOWER(r.name) LIKE '%owner%'
          OR LOWER(r.name) LIKE '%gerente%'
          OR LOWER(r.name) LIKE '%manager%'
          OR LOWER(r.name) LIKE '%recepción%'
          OR LOWER(r.name) LIKE '%recepcion%'
          OR LOWER(r.name) LIKE '%reception%'
          OR LOWER(r.name) LIKE '%administrador%'
      )
) eligible ON TRUE
WHERE client_create.code = 'client.create'
ON CONFLICT DO NOTHING;

-- The owner/settings console reads organization data. Granting organization.read
-- to roles that can already update the organization does not broaden their scope;
-- it only makes the read and write contract consistent.
INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT updater.role_id, organization_read.id
FROM permission organization_read
JOIN role_permission updater ON TRUE
JOIN permission update_permission
  ON update_permission.id = updater.permission_id
 AND update_permission.code = 'organization.update'
WHERE organization_read.code = 'organization.read'
ON CONFLICT DO NOTHING;
