INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('user.read', 'user.manage')
WHERE r.name = 'Gerente'
  AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;
