CREATE TABLE catalog_product_family (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    name VARCHAR(160) NOT NULL,
    brand VARCHAR(160),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_catalog_product_family_organization_id UNIQUE (organization_id, id)
);

CREATE UNIQUE INDEX uq_catalog_product_family_identity
    ON catalog_product_family (
        organization_id,
        LOWER(BTRIM(name)),
        LOWER(COALESCE(BTRIM(brand), ''))
    );

ALTER TABLE catalog_product
    ADD COLUMN family_id UUID,
    ADD COLUMN variant_name VARCHAR(160);

INSERT INTO catalog_product_family (
    id, organization_id, name, brand, description,
    created_at, updated_at, created_by, updated_by, version
)
SELECT DISTINCT ON (
        product.organization_id,
        LOWER(BTRIM(product.name)),
        LOWER(COALESCE(BTRIM(product.brand), ''))
    )
    product.id,
    product.organization_id,
    BTRIM(product.name),
    NULLIF(BTRIM(product.brand), ''),
    product.description,
    product.created_at,
    product.updated_at,
    product.created_by,
    product.updated_by,
    0
FROM catalog_product product
WHERE product.product_type = 'PHYSICAL'
  AND product.deleted_at IS NULL
ORDER BY
    product.organization_id,
    LOWER(BTRIM(product.name)),
    LOWER(COALESCE(BTRIM(product.brand), '')),
    product.created_at,
    product.id;

UPDATE catalog_product product
SET family_id = family.id,
    variant_name = COALESCE(NULLIF(BTRIM(product.presentation), ''), BTRIM(product.name))
FROM catalog_product_family family
WHERE product.product_type = 'PHYSICAL'
  AND product.deleted_at IS NULL
  AND family.organization_id = product.organization_id
  AND LOWER(BTRIM(family.name)) = LOWER(BTRIM(product.name))
  AND LOWER(COALESCE(BTRIM(family.brand), '')) = LOWER(COALESCE(BTRIM(product.brand), ''));

ALTER TABLE catalog_product
    ADD CONSTRAINT fk_catalog_product_family_tenant
        FOREIGN KEY (organization_id, family_id)
        REFERENCES catalog_product_family (organization_id, id);

CREATE INDEX ix_catalog_product_family
    ON catalog_product (organization_id, family_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE catalog_product_barcode (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    product_id UUID NOT NULL,
    barcode VARCHAR(128) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    CONSTRAINT fk_catalog_product_barcode_product_tenant
        FOREIGN KEY (organization_id, product_id)
        REFERENCES catalog_product (organization_id, id),
    CONSTRAINT ck_catalog_product_barcode_not_blank
        CHECK (BTRIM(barcode) <> '')
);

INSERT INTO catalog_product_barcode (
    id, organization_id, product_id, barcode, is_primary, active, created_at, created_by
)
SELECT
    gen_random_uuid(),
    product.organization_id,
    product.id,
    BTRIM(product.barcode),
    TRUE,
    product.archived_at IS NULL AND product.deleted_at IS NULL,
    product.created_at,
    product.created_by
FROM catalog_product product
WHERE product.barcode IS NOT NULL
  AND BTRIM(product.barcode) <> '';

DROP INDEX IF EXISTS uq_catalog_product_barcode_active;

CREATE UNIQUE INDEX uq_catalog_product_barcode_active
    ON catalog_product_barcode (organization_id, UPPER(BTRIM(barcode)))
    WHERE active = TRUE;

CREATE UNIQUE INDEX uq_catalog_product_primary_barcode_active
    ON catalog_product_barcode (organization_id, product_id)
    WHERE active = TRUE AND is_primary = TRUE;

CREATE INDEX ix_catalog_product_barcode_product
    ON catalog_product_barcode (organization_id, product_id, active, is_primary);
