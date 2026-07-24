CREATE TABLE IF NOT EXISTS catalog_product_branch (
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    product_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    PRIMARY KEY (branch_id, product_id),
    CONSTRAINT fk_catalog_product_branch_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_catalog_product_branch_product FOREIGN KEY (product_id) REFERENCES catalog_product(id)
);

CREATE INDEX IF NOT EXISTS ix_catalog_product_branch_lookup
    ON catalog_product_branch (organization_id, branch_id, product_id);

-- Legacy catalog entries did not record their originating branch. Keep them
-- available in every existing branch so upgrades do not hide operational data.
-- New products and plans are attached only to the active branch by application code.
INSERT INTO catalog_product_branch (organization_id, branch_id, product_id, created_at, created_by)
SELECT cp.organization_id, b.id, cp.id, COALESCE(cp.created_at, now()), cp.created_by
FROM catalog_product cp
JOIN branch b ON b.organization_id = cp.organization_id
WHERE cp.deleted_at IS NULL
ON CONFLICT DO NOTHING;
