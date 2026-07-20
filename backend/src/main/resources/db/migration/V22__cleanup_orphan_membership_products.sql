UPDATE catalog_product cp
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE cp.product_type = 'MEMBERSHIP_PLAN'
  AND cp.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM membership_plan mp
      WHERE mp.product_id = cp.id
        AND mp.deleted_at IS NULL
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sale_item si
      WHERE si.product_id = cp.id
  );
