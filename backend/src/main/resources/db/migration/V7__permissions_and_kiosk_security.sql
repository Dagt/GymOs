INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('8a000000-0000-0000-0000-000000000001', 'organization.update', 'Permiso organization.update', 'organization', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000002', 'branch.read', 'Permiso branch.read', 'branch', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000003', 'branch.update', 'Permiso branch.update', 'branch', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000004', 'sale.cancel', 'Permiso sale.cancel', 'sales', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000005', 'sale.refund', 'Permiso sale.refund', 'sales', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000006', 'payment.read', 'Permiso payment.read', 'payments', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000007', 'payment.manual.reject', 'Permiso payment.manual.reject', 'payments', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000008', 'payment.receipt.upload', 'Permiso payment.receipt.upload', 'payments', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000009', 'membership.read', 'Permiso membership.read', 'membership', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000010', 'membership.renew', 'Permiso membership.renew', 'membership', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000011', 'membership.change-plan', 'Permiso membership.change-plan', 'membership', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000012', 'membership.cancel', 'Permiso membership.cancel', 'membership', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000013', 'report.sales.read', 'Permiso report.sales.read', 'report', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'organization.update',
    'branch.read',
    'branch.update',
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
    'selfservice.terminal.manage'
)
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;
