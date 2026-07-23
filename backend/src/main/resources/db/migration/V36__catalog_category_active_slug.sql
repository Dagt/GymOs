ALTER TABLE catalog_category
    DROP CONSTRAINT IF EXISTS uq_catalog_category_slug;

DROP INDEX IF EXISTS uq_catalog_category_slug_active;

CREATE UNIQUE INDEX uq_catalog_category_slug_active
    ON catalog_category (organization_id, slug)
    WHERE deleted_at IS NULL;
