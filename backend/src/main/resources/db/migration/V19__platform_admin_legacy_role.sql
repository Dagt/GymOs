INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'platform.admin'
WHERE r.name IN ('Administrador', 'Administrador GymOS')
ON CONFLICT DO NOTHING;
