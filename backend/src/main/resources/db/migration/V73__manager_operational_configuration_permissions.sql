-- Gym managers act as tenant administrators for operational configuration.
INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code IN (
    'payment.settings.manage',
    'selfservice.settings.manage',
    'notification.settings.manage'
)
WHERE role.name = 'Gerente'
ON CONFLICT DO NOTHING;
