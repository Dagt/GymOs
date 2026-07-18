UPDATE role
SET name = 'Dueno del gimnasio',
    description = 'Administra su gimnasio, sucursales, equipo, configuracion y metricas de negocio.',
    updated_at = now()
WHERE name = 'Dueno / Gerencia';

UPDATE role
SET name = 'Recepcion',
    description = 'Opera mostrador, clientes, ventas, pagos, membresias y reservas por sucursal.',
    updated_at = now()
WHERE name = 'Recepcion / Caja';

UPDATE role
SET name = 'Gerente',
    description = 'Gestiona operacion diaria, reportes, inventario, agenda y equipo autorizado.',
    updated_at = now()
WHERE name = 'Operacion';

UPDATE role
SET description = 'Dueño de la plataforma SaaS GymOS; crea gimnasios, configura tenants y da soporte auditable.',
    updated_at = now()
WHERE name = 'Administrador GymOS';

UPDATE role
SET name = 'Dueno del gimnasio',
    description = 'Administrador principal del gimnasio dentro de su tenant.',
    updated_at = now()
WHERE name = 'Administrador'
  AND organization_id IS NOT NULL;

DELETE FROM role_permission
WHERE role_id IN (
    SELECT r.id
    FROM role r
    JOIN role_permission rp ON rp.role_id = r.id
    JOIN permission p ON p.id = rp.permission_id
    WHERE r.name = 'Dueno del gimnasio'
      AND p.code = 'platform.admin'
);

INSERT INTO user_role (user_id, role_id)
SELECT ua.id, r.id
FROM user_account ua
JOIN person p ON p.id = ua.person_id
JOIN role r ON r.name = 'Administrador GymOS'
WHERE LOWER(ua.username) = 'admin@gymos.local'
   OR LOWER(p.email) = 'admin@gymos.local'
ON CONFLICT DO NOTHING;

DELETE FROM role_permission
WHERE role_id IN (
    SELECT id FROM role WHERE name IN ('Dueno del gimnasio', 'Gerente', 'Recepcion', 'Coach')
);

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
    'report.owner.read',
    'report.coaches.read',
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
    'professional.read',
    'professional.manage',
    'notification.read',
    'notification.manage',
    'automation.read',
    'automation.manage',
    'onboarding.manage',
    'client.import',
    'benefit.read',
    'benefit.manage',
    'client.benefit.assign',
    'client.benefit.verify',
    'visit.read',
    'visit.create',
    'visit.consume',
    'booking.payment.manage',
    'booking.policy.manage',
    'notification.template.manage',
    'automation.execute',
    'automation.scheduler.manage',
    'operations.read'
)
WHERE r.name = 'Dueno del gimnasio'
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
    'client.create',
    'client.update',
    'catalog.read',
    'catalog.manage',
    'sale.read',
    'payment.read',
    'membership.read',
    'report.sales.read',
    'report.owner.read',
    'report.coaches.read',
    'access.read',
    'access.manage',
    'inventory.read',
    'inventory.manage',
    'resource.read',
    'booking.read',
    'booking.manage',
    'coach.read',
    'workout.read',
    'professional.read',
    'notification.read',
    'automation.read',
    'benefit.read',
    'visit.read',
    'visit.create',
    'visit.consume',
    'booking.payment.manage',
    'operations.read'
)
WHERE r.name = 'Gerente'
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
    'resource.read',
    'booking.read',
    'booking.manage',
    'benefit.read',
    'client.benefit.assign',
    'client.benefit.verify',
    'visit.read',
    'visit.create',
    'visit.consume'
)
WHERE r.name = 'Recepcion'
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
    'workout.manage',
    'report.coaches.read'
)
WHERE r.name = 'Coach'
ON CONFLICT DO NOTHING;
