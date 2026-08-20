CREATE TABLE organization_payment_configuration (
    organization_id UUID PRIMARY KEY REFERENCES organization(id),
    reception_manual_terminal_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reception_mercado_pago_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mercado_pago_account_id VARCHAR(160),
    mercado_pago_terminal_id VARCHAR(160),
    mercado_pago_connected_at TIMESTAMPTZ,
    stripe_self_checkout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_recurring_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_account_id VARCHAR(160),
    stripe_connected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO organization_payment_configuration (
    organization_id,
    reception_manual_terminal_enabled,
    reception_mercado_pago_enabled,
    stripe_self_checkout_enabled,
    stripe_recurring_enabled,
    created_at,
    updated_at,
    version
)
SELECT id, TRUE, FALSE, FALSE, FALSE, now(), now(), 0
FROM organization
ON CONFLICT (organization_id) DO NOTHING;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('8a000000-0000-0000-0000-000000000019', 'payment.settings.read', 'Consultar modos de cobro habilitados', 'payments', now(), now(), 0),
    ('8a000000-0000-0000-0000-000000000020', 'payment.settings.manage', 'Configurar modos y proveedores de cobro', 'payments', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'payment.settings.read'
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio', 'Gerente', 'Recepcion')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'payment.settings.manage'
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio')
ON CONFLICT DO NOTHING;
