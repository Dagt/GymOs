INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '8a000000-0000-0000-0000-000000000021',
    'payment.provider.stripe.manage',
    'Vincular y verificar la cuenta de Stripe del gimnasio',
    'payments',
    now(),
    now(),
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.code = 'payment.provider.stripe.manage'
WHERE role.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio')
ON CONFLICT DO NOTHING;

-- Managers keep payment.settings.manage so they can enable or disable an
-- already connected provider in the kiosk, but they cannot own the Stripe
-- onboarding lifecycle.
DELETE FROM role_permission role_permission
USING role, permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND LOWER(role.name) IN ('gerente', 'gerente de sucursal')
  AND permission.code = 'payment.provider.stripe.manage';
