-- Managers are responsible for day-to-day automation rules and delivery recovery in their branch.
INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code IN (
    'automation.read',
    'automation.manage',
    'automation.execute',
    'notification.read',
    'notification.manage'
)
WHERE role.name = 'Gerente'
ON CONFLICT DO NOTHING;
