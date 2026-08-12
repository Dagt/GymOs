-- Managers operate the day-to-day catalog for their selected branch.
INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code IN (
    'benefit.manage',
    'resource.manage'
)
WHERE role.name = 'Gerente'
ON CONFLICT DO NOTHING;
