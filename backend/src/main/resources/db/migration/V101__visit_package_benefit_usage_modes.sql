ALTER TABLE visit_package_plan_benefit
    ADD COLUMN usage_mode VARCHAR(32) NOT NULL DEFAULT 'CONSUMES_VISIT',
    ADD CONSTRAINT ck_visit_package_plan_benefit_usage_mode
        CHECK (usage_mode IN ('CONSUMES_VISIT', 'INCLUDED_WITH_VISIT'));

ALTER TABLE visit_package_entitlement_benefit
    ADD COLUMN usage_mode VARCHAR(32) NOT NULL DEFAULT 'CONSUMES_VISIT',
    ADD CONSTRAINT ck_visit_package_entitlement_benefit_usage_mode
        CHECK (usage_mode IN ('CONSUMES_VISIT', 'INCLUDED_WITH_VISIT'));

ALTER TABLE visit_package_entitlement_benefit_branch
    ADD COLUMN usage_mode VARCHAR(32) NOT NULL DEFAULT 'CONSUMES_VISIT',
    ADD CONSTRAINT ck_visit_package_entitlement_benefit_branch_usage_mode
        CHECK (usage_mode IN ('CONSUMES_VISIT', 'INCLUDED_WITH_VISIT'));

ALTER TABLE visit_package_consumption
    ADD COLUMN benefit_id UUID,
    ADD COLUMN business_date DATE;

UPDATE visit_package_consumption consumption
SET business_date = (consumption.occurred_at AT TIME ZONE branch.timezone)::date
FROM branch
WHERE branch.organization_id = consumption.organization_id
  AND branch.id = consumption.branch_id
  AND consumption.business_date IS NULL;

ALTER TABLE visit_package_consumption
    ALTER COLUMN business_date SET NOT NULL,
    ADD CONSTRAINT fk_visit_package_consumption_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id);

CREATE UNIQUE INDEX ux_visit_package_consumption_base_context
    ON visit_package_consumption
       (id, organization_id, entitlement_id, branch_id, business_date);

CREATE INDEX ix_visit_package_consumption_base_visit
    ON visit_package_consumption
       (organization_id, entitlement_id, branch_id, business_date, status, occurred_at);

CREATE TABLE visit_package_included_access (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entitlement_id UUID NOT NULL,
    benefit_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    access_device_id UUID,
    base_consumption_id UUID,
    access_log_id UUID REFERENCES access_log(id),
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    remaining_after INTEGER NOT NULL CHECK (remaining_after >= 0),
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_visit_package_included_access_entitlement
        FOREIGN KEY (entitlement_id, organization_id)
        REFERENCES visit_package_entitlement(id, organization_id),
    CONSTRAINT fk_visit_package_included_access_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id),
    CONSTRAINT fk_visit_package_included_access_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id),
    CONSTRAINT fk_visit_package_included_access_device
        FOREIGN KEY (access_device_id, organization_id)
        REFERENCES access_device(id, organization_id),
    CONSTRAINT fk_visit_package_included_access_base_consumption
        FOREIGN KEY (
            base_consumption_id, organization_id, entitlement_id, branch_id, business_date
        ) REFERENCES visit_package_consumption(
            id, organization_id, entitlement_id, branch_id, business_date
        ),
    CONSTRAINT ck_visit_package_included_access_status
        CHECK (status IN ('GRANTED', 'DENIED')),
    CONSTRAINT ck_visit_package_included_access_base
        CHECK (
            (status = 'GRANTED' AND base_consumption_id IS NOT NULL)
            OR (status = 'DENIED' AND base_consumption_id IS NULL)
        )
);

CREATE UNIQUE INDEX ux_visit_package_included_access_idempotency
    ON visit_package_included_access (organization_id, idempotency_key);

CREATE INDEX ix_visit_package_included_access_entitlement_date
    ON visit_package_included_access
       (organization_id, entitlement_id, branch_id, business_date, occurred_at DESC);

COMMENT ON COLUMN visit_package_plan_benefit.usage_mode IS
    'Semántica comercial de la relación paquete-beneficio; nunca es una propiedad intrínseca del Benefit.';

COMMENT ON COLUMN visit_package_entitlement_benefit_branch.usage_mode IS
    'Snapshot exacto del modo comprado para un beneficio en una sucursal.';

COMMENT ON COLUMN visit_package_consumption.business_date IS
    'Fecha operativa de la sucursal utilizada para reglas de acceso incluido.';

COMMENT ON TABLE visit_package_included_access IS
    'Auditoría idempotente de accesos incluidos concedidos o denegados; nunca reduce saldo y vincula el consumo base cuando concede.';
