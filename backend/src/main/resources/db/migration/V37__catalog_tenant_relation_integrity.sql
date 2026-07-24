ALTER TABLE catalog_product
    ADD CONSTRAINT uq_catalog_product_organization_id UNIQUE (organization_id, id);

ALTER TABLE catalog_category
    ADD CONSTRAINT uq_catalog_category_organization_id UNIQUE (organization_id, id);

ALTER TABLE catalog_tag
    ADD CONSTRAINT uq_catalog_tag_organization_id UNIQUE (organization_id, id);

ALTER TABLE catalog_category
    DROP CONSTRAINT IF EXISTS fk_catalog_category_parent,
    ADD CONSTRAINT fk_catalog_category_parent_tenant
        FOREIGN KEY (organization_id, parent_id)
        REFERENCES catalog_category (organization_id, id);

ALTER TABLE catalog_product_category
    DROP CONSTRAINT IF EXISTS fk_catalog_product_category_product,
    DROP CONSTRAINT IF EXISTS fk_catalog_product_category_category,
    ADD CONSTRAINT fk_catalog_product_category_product_tenant
        FOREIGN KEY (organization_id, product_id)
        REFERENCES catalog_product (organization_id, id),
    ADD CONSTRAINT fk_catalog_product_category_category_tenant
        FOREIGN KEY (organization_id, category_id)
        REFERENCES catalog_category (organization_id, id);

ALTER TABLE catalog_product_tag
    DROP CONSTRAINT IF EXISTS fk_catalog_product_tag_product,
    DROP CONSTRAINT IF EXISTS fk_catalog_product_tag_tag,
    ADD CONSTRAINT fk_catalog_product_tag_product_tenant
        FOREIGN KEY (organization_id, product_id)
        REFERENCES catalog_product (organization_id, id),
    ADD CONSTRAINT fk_catalog_product_tag_tag_tenant
        FOREIGN KEY (organization_id, tag_id)
        REFERENCES catalog_tag (organization_id, id);
