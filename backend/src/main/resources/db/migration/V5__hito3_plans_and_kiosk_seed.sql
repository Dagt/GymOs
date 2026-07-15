INSERT INTO catalog_product (
    id, organization_id, sku, name, description, product_type, price_cents,
    currency, taxable, status, created_at, updated_at, version
)
VALUES (
    '66666666-6666-6666-6666-666666666603',
    '11111111-1111-1111-1111-111111111111',
    'PLAN-TRIMESTRAL',
    'Membresia trimestral',
    'Acceso general por 90 dias.',
    'MEMBERSHIP_PLAN',
    189900,
    'MXN',
    TRUE,
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO membership_plan (
    id, organization_id, product_id, duration_days, grace_days, benefits_json,
    status, created_at, updated_at, version
)
VALUES (
    '77777777-7777-7777-7777-777777777702',
    '11111111-1111-1111-1111-111111111111',
    '66666666-6666-6666-6666-666666666603',
    90,
    5,
    '{"access":"general","sessions":"unlimited"}'::jsonb,
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO self_service_terminal (
    id, organization_id, branch_id, name, device_code, status,
    allowed_actions_json, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999901',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Kiosco Principal',
    'KIOSK-MAIN',
    'ACTIVE',
    '{"renew":true,"changePlan":true,"payments":true}'::jsonb,
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;
