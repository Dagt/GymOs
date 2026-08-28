ALTER TABLE visit_package_entitlement
    ADD COLUMN validity_days INTEGER;

UPDATE visit_package_entitlement entitlement
SET validity_days = plan.validity_days
FROM visit_package_plan plan
WHERE plan.organization_id = entitlement.organization_id
  AND plan.id = entitlement.visit_package_plan_id;

ALTER TABLE visit_package_entitlement
    ALTER COLUMN validity_days SET NOT NULL,
    ADD CONSTRAINT ck_visit_package_entitlement_validity_days CHECK (validity_days > 0);

COMMENT ON COLUMN visit_package_entitlement.validity_days IS
    'Snapshot de vigencia comprado; editar la definición no altera un Checkout pendiente ni un saldo activo.';
