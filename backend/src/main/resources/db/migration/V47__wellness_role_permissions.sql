-- Grant professional permissions to Gerente and Recepcion system roles
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('professional.read', 'professional.manage')
WHERE r.name IN ('Gerente', 'Gerente de sucursal')
   OR LOWER(r.name) LIKE '%gerente%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('professional.read')
WHERE r.name IN ('Recepcion', 'Recepcion / Caja')
   OR LOWER(r.name) LIKE '%recepcion%'
ON CONFLICT DO NOTHING;
