-- Configuración interna de autoservicio por sucursal.
--
-- El código generado no identifica un dispositivo físico ni forma parte de la
-- URL pública. Permite reutilizar temporalmente self_service_terminal como
-- almacenamiento de capacidades mientras el kiosco se inicia desde una sesión
-- autenticada de recepción.

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
    b.organization_id,
    b.id,
    'Autoservicio - ' || b.name,
    'KIOSK-BRANCH-' || b.id::text,
    'ACTIVE',
    '{"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":true,"profileUpdate":true,"autopay":true}'::jsonb,
    now(),
    now(),
    0
FROM branch b
JOIN organization o
  ON o.id = b.organization_id
 AND o.deleted_at IS NULL
WHERE b.deleted_at IS NULL
  AND b.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM self_service_terminal existing
      WHERE existing.organization_id = b.organization_id
        AND existing.branch_id = b.id
        AND existing.device_code = 'KIOSK-BRANCH-' || b.id::text
        AND existing.deleted_at IS NULL
  );
