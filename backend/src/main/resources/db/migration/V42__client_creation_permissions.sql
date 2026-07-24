-- Grant client creation to owner, management, reception and platform support
-- roles. Coaches can retain client visibility for assigned work but are excluded
-- because they do not have client.manage, sale.create or platform.admin.
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
) eligible ON TRUE
WHERE client_create.code = 'client.create'
ON CONFLICT DO NOTHING;
