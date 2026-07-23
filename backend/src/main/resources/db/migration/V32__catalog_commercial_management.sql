ALTER TABLE catalog_product
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(128),
    ADD COLUMN IF NOT EXISTS brand VARCHAR(160),
    ADD COLUMN IF NOT EXISTS presentation VARCHAR(160),
    ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(40),
    ADD COLUMN IF NOT EXISTS inventory_tracked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS image_object_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by UUID;

UPDATE catalog_product
SET inventory_tracked = TRUE
WHERE product_type = 'PHYSICAL'
  AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS catalog_category (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    parent_id UUID,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_catalog_category_parent FOREIGN KEY (parent_id) REFERENCES catalog_category(id),
    CONSTRAINT uq_catalog_category_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_catalog_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE IF NOT EXISTS catalog_product_category (
    organization_id UUID NOT NULL,
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_catalog_product_category_product FOREIGN KEY (product_id) REFERENCES catalog_product(id),
    CONSTRAINT fk_catalog_product_category_category FOREIGN KEY (category_id) REFERENCES catalog_category(id)
);

CREATE TABLE IF NOT EXISTS catalog_tag (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    CONSTRAINT uq_catalog_tag_name UNIQUE (organization_id, normalized_name)
);

CREATE TABLE IF NOT EXISTS catalog_product_tag (
    organization_id UUID NOT NULL,
    product_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_catalog_product_tag_product FOREIGN KEY (product_id) REFERENCES catalog_product(id),
    CONSTRAINT fk_catalog_product_tag_tag FOREIGN KEY (tag_id) REFERENCES catalog_tag(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_product_barcode_active
    ON catalog_product (organization_id, barcode)
    WHERE barcode IS NOT NULL AND deleted_at IS NULL AND archived_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_catalog_product_search
    ON catalog_product (organization_id, status, product_type, display_order, name);

CREATE INDEX IF NOT EXISTS ix_catalog_product_inventory_tracked
    ON catalog_product (organization_id, inventory_tracked)
    WHERE deleted_at IS NULL AND archived_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_catalog_category_organization
    ON catalog_category (organization_id, status, display_order, name)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_catalog_product_category_category
    ON catalog_product_category (organization_id, category_id, product_id);
