ALTER TABLE membership_plan
    ADD COLUMN self_service_acquisition_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN registration_fee_policy VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE membership_plan
    ADD CONSTRAINT ck_membership_plan_registration_fee_policy
        CHECK (registration_fee_policy IN ('DEFAULT', 'WAIVED'));

CREATE INDEX ix_membership_plan_org_self_service
    ON membership_plan (organization_id, self_service_acquisition_enabled, status)
    WHERE deleted_at IS NULL;

CREATE TABLE pricing_benefit_membership_plan (
    benefit_id UUID NOT NULL,
    membership_plan_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    PRIMARY KEY (benefit_id, membership_plan_id),
    CONSTRAINT fk_pricing_benefit_plan_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pricing_benefit_plan_membership_plan
        FOREIGN KEY (membership_plan_id, organization_id)
        REFERENCES membership_plan(id, organization_id)
        ON DELETE CASCADE
);

CREATE INDEX ix_pricing_benefit_plan_org_plan
    ON pricing_benefit_membership_plan (organization_id, membership_plan_id);

COMMENT ON COLUMN membership_plan.self_service_acquisition_enabled IS
    'Permite nuevas altas y cambios hacia el plan desde autoservicio; no impide mostrar o renovar una membresía existente.';

COMMENT ON COLUMN membership_plan.registration_fee_policy IS
    'DEFAULT usa la política general de inscripción; WAIVED nunca agrega inscripción para el plan objetivo.';

COMMENT ON TABLE pricing_benefit_membership_plan IS
    'Planes sobre los que puede aplicarse un beneficio económico SPECIAL_PRICE; no expresa beneficios ACCESS concedidos por el plan.';
