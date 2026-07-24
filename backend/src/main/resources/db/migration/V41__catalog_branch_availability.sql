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

-- Recover the most reliable historical scope first: a product belongs to every
-- branch where it already has an inventory record.
INSERT INTO catalog_product_branch (organization_id, branch_id, product_id, created_at, created_by)
SELECT DISTINCT cp.organization_id, stock.branch_id, cp.id,
       COALESCE(stock.created_at, cp.created_at, now()), cp.created_by
FROM catalog_product cp
JOIN inventory_stock stock
  ON stock.organization_id = cp.organization_id
 AND stock.product_id = cp.id
 AND stock.deleted_at IS NULL
WHERE cp.deleted_at IS NULL
ON CONFLICT DO NOTHING;

-- Products and plans also belong to branches where they have already been sold.
INSERT INTO catalog_product_branch (organization_id, branch_id, product_id, created_at, created_by)
SELECT DISTINCT cp.organization_id, sale.branch_id, cp.id,
       COALESCE(sale.created_at, cp.created_at, now()), cp.created_by
FROM catalog_product cp
JOIN sale_item item
  ON item.organization_id = cp.organization_id
 AND item.product_id = cp.id
JOIN sale sale
  ON sale.organization_id = item.organization_id
 AND sale.id = item.sale_id
WHERE cp.deleted_at IS NULL
  AND sale.branch_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Older records without stock or sales have no trustworthy branch metadata.
-- Assign each one to a single branch instead of replicating it across the whole
-- organization. New records are attached to the active branch by application code.
INSERT INTO catalog_product_branch (organization_id, branch_id, product_id, created_at, created_by)
SELECT cp.organization_id, fallback_branch.id, cp.id,
       COALESCE(cp.created_at, now()), cp.created_by
FROM catalog_product cp
JOIN LATERAL (
    SELECT branch.id
    FROM branch
    WHERE branch.organization_id = cp.organization_id
    ORDER BY (branch.status = 'ACTIVE') DESC, branch.id
    LIMIT 1
) fallback_branch ON TRUE
WHERE cp.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM catalog_product_branch existing
      WHERE existing.organization_id = cp.organization_id
        AND existing.product_id = cp.id
  )
ON CONFLICT DO NOTHING;
