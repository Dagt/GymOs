INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('8a000000-0000-0000-0000-000000000101', 'branch.create', 'Permiso branch.create', 'branch', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000102', 'user.read', 'Permiso user.read', 'identity', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000103', 'user.manage', 'Permiso user.manage', 'identity', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role (id, organization_id, name, description, system_role, created_at, updated_at, version)
VALUES
    ('91000000-0000-0000-0000-000000000001', NULL, 'Administrador GymOS', 'Configura gimnasios, sucursales, equipo y permisos.', true, now(), now(), 0),
    ('91000000-0000-0000-0000-000000000002', NULL, 'Dueno / Gerencia', 'Consulta reportes y administra configuracion de negocio.', true, now(), now(), 0),
    ('91000000-0000-0000-0000-000000000003', NULL, 'Recepcion / Caja', 'Atiende socios, cobra ventas y valida pagos manuales.', true, now(), now(), 0),
    ('91000000-0000-0000-0000-000000000004', NULL, 'Coach', 'Consulta socios, rutinas y asignaciones de entrenamiento.', true, now(), now(), 0),
    ('91000000-0000-0000-0000-000000000005', NULL, 'Operacion', 'Apoya accesos, reservas e inventario.', true, now(), now(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'organization.update',
    'branch.read',
    'branch.create',
    'branch.update',
    'user.read',
    'user.manage',
    'client.read',
    'client.create',
    'client.update',
    'catalog.read',
    'catalog.manage',
    'sale.read',
    'sale.create',
    'sale.cancel',
    'sale.refund',
    'payment.read',
    'payment.manual.create',
    'payment.manual.validate',
    'payment.manual.reject',
    'payment.receipt.upload',
    'membership.read',
    'membership.renew',
    'membership.change-plan',
    'membership.cancel',
    'report.sales.read',
    'audit.read',
    'selfservice.terminal.manage',
    'access.read',
    'access.manage',
    'inventory.read',
    'inventory.manage',
    'resource.read',
    'resource.manage',
    'booking.read',
    'booking.manage',
    'coach.read',
    'coach.manage',
    'workout.read',
    'workout.manage',
    'report.owner.read',
    'onboarding.manage',
    'benefit.read',
    'benefit.manage'
)
WHERE r.name IN ('Administrador', 'Administrador GymOS')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'branch.read',
    'branch.update',
    'user.read',
    'client.read',
    'catalog.read',
    'catalog.manage',
    'sale.read',
    'payment.read',
    'membership.read',
    'report.sales.read',
    'report.owner.read',
    'benefit.read',
    'benefit.manage'
)
WHERE r.name = 'Dueno / Gerencia'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'branch.read',
    'client.read',
    'client.create',
    'client.update',
    'catalog.read',
    'sale.read',
    'sale.create',
    'payment.read',
    'payment.manual.create',
    'payment.manual.validate',
    'payment.manual.reject',
    'payment.receipt.upload',
    'membership.read',
    'membership.renew',
    'membership.change-plan',
    'report.sales.read',
    'benefit.read',
    'client.benefit.assign',
    'client.benefit.verify'
)
WHERE r.name = 'Recepcion / Caja'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'branch.read',
    'client.read',
    'membership.read',
    'coach.read',
    'workout.read',
    'workout.manage'
)
WHERE r.name = 'Coach'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'branch.read',
    'client.read',
    'access.read',
    'access.manage',
    'inventory.read',
    'inventory.manage',
    'resource.read',
    'resource.manage',
    'booking.read',
    'booking.manage',
    'visit.read',
    'visit.create',
    'visit.consume'
)
WHERE r.name = 'Operacion'
ON CONFLICT DO NOTHING;
