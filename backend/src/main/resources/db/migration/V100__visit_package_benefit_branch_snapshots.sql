CREATE TABLE visit_package_entitlement_benefit_branch (
    entitlement_id UUID NOT NULL,
    benefit_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entitlement_id, benefit_id, branch_id),
    CONSTRAINT fk_visit_package_entitlement_benefit_branch_entitlement
        FOREIGN KEY (entitlement_id, organization_id)
        REFERENCES visit_package_entitlement(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_entitlement_benefit_branch_snapshot_benefit
        FOREIGN KEY (entitlement_id, benefit_id)
        REFERENCES visit_package_entitlement_benefit(entitlement_id, benefit_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_entitlement_benefit_branch_snapshot_branch
        FOREIGN KEY (entitlement_id, branch_id)
        REFERENCES visit_package_entitlement_branch(entitlement_id, branch_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_entitlement_benefit_branch_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id),
    CONSTRAINT fk_visit_package_entitlement_benefit_branch_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id)
);

CREATE INDEX ix_visit_package_entitlement_benefit_branch_lookup
    ON visit_package_entitlement_benefit_branch
       (organization_id, entitlement_id, branch_id, benefit_id);

INSERT INTO visit_package_entitlement_benefit_branch (
    entitlement_id, benefit_id, branch_id, organization_id, created_at
)
SELECT entitlement_benefit.entitlement_id,
       entitlement_benefit.benefit_id,
       entitlement_branch.branch_id,
       entitlement_benefit.organization_id,
       entitlement_benefit.created_at
FROM visit_package_entitlement_benefit entitlement_benefit
JOIN visit_package_entitlement_branch entitlement_branch
  ON entitlement_branch.organization_id = entitlement_benefit.organization_id
 AND entitlement_branch.entitlement_id = entitlement_benefit.entitlement_id
JOIN benefit benefit
  ON benefit.organization_id = entitlement_benefit.organization_id
 AND benefit.id = entitlement_benefit.benefit_id
WHERE benefit.deleted_at IS NULL
  AND benefit.benefit_type = 'ACCESS'
  AND (
      (benefit.access_scope = 'LOCAL'
       AND (benefit.branch_id IS NULL OR benefit.branch_id = entitlement_branch.branch_id))
      OR
      (benefit.access_scope = 'PLAN_COVERAGE' AND EXISTS (
          SELECT 1
          FROM benefit_membership_plan benefit_plan
          JOIN membership_plan membership_plan
            ON membership_plan.organization_id = benefit_plan.organization_id
           AND membership_plan.id = benefit_plan.membership_plan_id
           AND membership_plan.deleted_at IS NULL
          WHERE benefit_plan.organization_id = benefit.organization_id
            AND benefit_plan.benefit_id = benefit.id
            AND (
                membership_plan.multi_branch_access = TRUE
                OR EXISTS (
                    SELECT 1
                    FROM catalog_product_branch product_branch
                    WHERE product_branch.organization_id = membership_plan.organization_id
                      AND product_branch.product_id = membership_plan.product_id
                      AND product_branch.branch_id = entitlement_branch.branch_id
                )
            )
      ))
  );

COMMENT ON TABLE visit_package_entitlement_benefit_branch IS
    'Snapshot de la intersección entre sucursales del paquete y alcance real de cada acceso; el paquete nunca amplía un beneficio.';
