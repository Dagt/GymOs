INSERT INTO self_service_terminal (
    id,
    organization_id,
    branch_id,
    name,
    device_code,
    status,
    allowed_actions_json,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    o.id,
    b.id,
    'Kiosco Principal',
    'KIOSK-MAIN',
    'ACTIVE',
    '{"renew":true,"changePlan":true,"payments":true}'::jsonb,
    now(),
    now(),
    0
FROM organization o
JOIN LATERAL (
    SELECT id
    FROM branch
    WHERE organization_id = o.id
      AND status = 'ACTIVE'
      AND deleted_at IS NULL
    ORDER BY name
    LIMIT 1
) b ON true
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM self_service_terminal sst
      WHERE sst.organization_id = o.id
        AND sst.device_code = 'KIOSK-MAIN'
        AND sst.deleted_at IS NULL
  );
