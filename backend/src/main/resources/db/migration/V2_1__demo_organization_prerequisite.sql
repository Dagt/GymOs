INSERT INTO organization (
    id, legal_name, trade_name, tax_id, status, timezone, default_currency,
    contact_email, contact_phone, created_at, updated_at, version
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'GymOS Demo S.A. de C.V.',
    'GymOS Demo',
    'GYMOS-DEMO',
    'ACTIVE',
    'America/Mexico_City',
    'MXN',
    'admin@gymos.local',
    '+520000000000',
    now(),
    now(),
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO branch (
    id, organization_id, name, code, timezone, status, phone, created_at, updated_at, version
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Sucursal Principal',
    'MAIN',
    'America/Mexico_City',
    'ACTIVE',
    '+520000000000',
    now(),
    now(),
    0
)
ON CONFLICT (id) DO NOTHING;
