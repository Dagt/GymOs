-- Repair operational settings access for managers created before the configuration modules.
INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code IN (
    'organization.read',
    'branch.read',
    'payment.settings.read',
    'payment.settings.manage',
    'selfservice.settings.manage',
    'notification.settings.manage'
)
WHERE LOWER(role.name) IN ('gerente', 'gerente de sucursal')
ON CONFLICT DO NOTHING;
