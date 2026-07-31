-- Provisiona un terminal inicial por organización para que el acceso desde
-- recepción nunca dependa del terminal perteneciente al tenant demo.
--
-- KIOSK-MAIN continúa siendo una etiqueta temporal. El diseño definitivo
-- reemplazará este código por un identificador público global y credenciales
-- de dispositivo, pero mientras tanto cada organización debe tener su propio
-- registro aislado.

WITH default_branch AS (
    SELECT DISTINCT ON (b.organization_id)
           b.organization_id,
           b.id AS branch_id,
           b.name AS branch_name
    FROM branch b
    WHERE b.deleted_at IS NULL
      AND b.status = 'ACTIVE'
    ORDER BY b.organization_id, b.created_at, b.id
)
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
    db.organization_id,
    db.branch_id,
    'Kiosco principal - ' || db.branch_name,
    'KIOSK-MAIN',
    'ACTIVE',
    '{"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":true,"profileUpdate":true,"autopay":true}'::jsonb,
    now(),
    now(),
    0
FROM default_branch db
JOIN organization o
  ON o.id = db.organization_id
 AND o.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM self_service_terminal existing
    WHERE existing.organization_id = db.organization_id
      AND existing.device_code = 'KIOSK-MAIN'
      AND existing.deleted_at IS NULL
);
