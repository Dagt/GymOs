ALTER TABLE benefit
    ADD COLUMN access_scope VARCHAR(24) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE benefit
    ADD CONSTRAINT ck_benefit_access_scope
        CHECK (access_scope IN ('LOCAL', 'PLAN_COVERAGE'));

CREATE INDEX ix_benefit_org_access_scope
    ON benefit (organization_id, access_scope, status)
    WHERE deleted_at IS NULL AND benefit_type = 'ACCESS';

COMMENT ON COLUMN benefit.access_scope IS
    'LOCAL limita el beneficio ACCESS a benefit.branch_id; PLAN_COVERAGE lo reconoce donde sea válido el plan que lo concede.';
