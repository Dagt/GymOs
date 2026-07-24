-- Registration of members is an operational capability for owner, manager,
-- reception and platform support. Coaches remain read-only because they do not
-- receive sale.create, membership.create or platform.admin.
INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT role_capability.role_id, client_create.id
FROM role_permission role_capability
JOIN permission capability ON capability.id = role_capability.permission_id
JOIN permission client_create ON client_create.code = 'client.create'
WHERE capability.code IN ('sale.create', 'membership.create', 'platform.admin')
ON CONFLICT DO NOTHING;
