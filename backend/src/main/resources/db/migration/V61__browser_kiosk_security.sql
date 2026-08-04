ALTER TABLE self_service_session
    ADD COLUMN launched_by_user_id UUID REFERENCES user_account(id);

DROP INDEX IF EXISTS ux_terminal_org_device;

CREATE UNIQUE INDEX ux_terminal_org_branch_device
    ON self_service_terminal (organization_id, branch_id, device_code)
    WHERE deleted_at IS NULL;

INSERT INTO self_service_terminal (
    id, organization_id, branch_id, name, device_code, status,
    allowed_actions_json, created_at, updated_at, version
)
SELECT
    gen_random_uuid(), b.organization_id, b.id, 'Kiosco de sucursal',
    'KIOSK-MAIN', 'ACTIVE',
    '{"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":false}'::jsonb,
    now(), now(), 0
FROM branch b
JOIN organization o ON o.id = b.organization_id
WHERE b.status = 'ACTIVE'
  AND b.deleted_at IS NULL
  AND o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM self_service_terminal sst
      WHERE sst.organization_id = b.organization_id
        AND sst.branch_id = b.id
        AND sst.status = 'ACTIVE'
        AND sst.deleted_at IS NULL
  );

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '8a000000-0000-0000-0000-000000000018',
    'selfservice.session.launch',
    'Iniciar el modo kiosco restringido desde una sesion operativa',
    'selfservice',
    now(),
    now(),
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'selfservice.session.launch'
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio', 'Gerente', 'Recepcion')
ON CONFLICT DO NOTHING;

UPDATE self_service_terminal
SET allowed_actions_json = allowed_actions_json
        || jsonb_build_object('wellnessBooking', false, 'autopay', false),
    updated_at = now(),
    version = version + 1
WHERE deleted_at IS NULL;

ALTER TABLE self_service_terminal
    ALTER COLUMN allowed_actions_json SET DEFAULT
    '{"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":false}'::jsonb;
