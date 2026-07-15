CREATE UNIQUE INDEX ux_inventory_movement_sale_product_effect
    ON inventory_movement (organization_id, branch_id, reference_type, reference_id, product_id)
    WHERE reference_type = 'SALE' AND reference_id IS NOT NULL;
