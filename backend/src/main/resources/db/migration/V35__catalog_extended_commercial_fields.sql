ALTER TABLE catalog_product
    ADD COLUMN IF NOT EXISTS commercial_subtype VARCHAR(40) NOT NULL DEFAULT 'GENERAL_PRODUCT',
    ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS reference_cost_cents BIGINT,
    ADD COLUMN IF NOT EXISTS promotional_price_cents BIGINT,
    ADD COLUMN IF NOT EXISTS promotion_starts_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS promotion_ends_at TIMESTAMPTZ;

UPDATE catalog_product
SET commercial_subtype = CASE
    WHEN product_type = 'SERVICE' THEN 'GENERAL_SERVICE'
    WHEN product_type = 'MEMBERSHIP_PLAN' THEN 'MEMBERSHIP'
    ELSE 'GENERAL_PRODUCT'
END
WHERE commercial_subtype = 'GENERAL_PRODUCT';

ALTER TABLE catalog_product
    ADD CONSTRAINT ck_catalog_product_reference_cost_non_negative
        CHECK (reference_cost_cents IS NULL OR reference_cost_cents >= 0),
    ADD CONSTRAINT ck_catalog_product_promotional_price_non_negative
        CHECK (promotional_price_cents IS NULL OR promotional_price_cents >= 0),
    ADD CONSTRAINT ck_catalog_product_promotion_dates
        CHECK (promotion_ends_at IS NULL OR promotion_starts_at IS NULL OR promotion_ends_at > promotion_starts_at);

CREATE INDEX IF NOT EXISTS ix_catalog_product_commercial_subtype
    ON catalog_product (organization_id, commercial_subtype, status)
    WHERE deleted_at IS NULL AND archived_at IS NULL;
