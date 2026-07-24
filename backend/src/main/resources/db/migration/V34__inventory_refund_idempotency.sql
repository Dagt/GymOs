CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_movement_refund_product
    ON inventory_movement (organization_id, branch_id, product_id, reference_type, reference_id)
    WHERE reference_type = 'SALE_REFUND' AND reference_id IS NOT NULL;
