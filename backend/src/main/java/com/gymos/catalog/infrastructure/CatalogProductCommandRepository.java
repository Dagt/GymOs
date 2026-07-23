package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogProductCommandPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogProductCommandRepository implements CatalogProductCommandPort {
	private final JdbcClient jdbcClient;

	public CatalogProductCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void create(ProductMutation product) {
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, description, product_type, price_cents,
				    currency, taxable, status, barcode, brand, presentation, unit_of_measure,
				    inventory_tracked, display_order, image_object_key,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :sku, :name, :description, :productType, :priceCents,
				    :currency, :taxable, :status, :barcode, :brand, :presentation, :unitOfMeasure,
				    :inventoryTracked, :displayOrder, :imageObjectKey,
				    :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", product.productId())
				.param("organizationId", product.organizationId())
				.param("sku", product.sku())
				.param("name", product.name())
				.param("description", product.description())
				.param("productType", product.productType())
				.param("priceCents", product.priceCents())
				.param("currency", product.currency())
				.param("taxable", product.taxable())
				.param("status", product.status())
				.param("barcode", product.barcode())
				.param("brand", product.brand())
				.param("presentation", product.presentation())
				.param("unitOfMeasure", product.unitOfMeasure())
				.param("inventoryTracked", product.inventoryTracked())
				.param("displayOrder", product.displayOrder())
				.param("imageObjectKey", product.imageObjectKey())
				.param("now", product.now())
				.param("userId", product.userId())
				.update();
	}

	@Override
	public int update(ProductMutation product) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET sku = :sku, name = :name, description = :description, product_type = :productType,
				    price_cents = :priceCents, currency = :currency, taxable = :taxable, status = :status,
				    barcode = :barcode, brand = :brand, presentation = :presentation,
				    unit_of_measure = :unitOfMeasure, inventory_tracked = :inventoryTracked,
				    display_order = :displayOrder, image_object_key = :imageObjectKey,
				    updated_at = :now, updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :id
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""")
				.param("sku", product.sku())
				.param("name", product.name())
				.param("description", product.description())
				.param("productType", product.productType())
				.param("priceCents", product.priceCents())
				.param("currency", product.currency())
				.param("taxable", product.taxable())
				.param("status", product.status())
				.param("barcode", product.barcode())
				.param("brand", product.brand())
				.param("presentation", product.presentation())
				.param("unitOfMeasure", product.unitOfMeasure())
				.param("inventoryTracked", product.inventoryTracked())
				.param("displayOrder", product.displayOrder())
				.param("imageObjectKey", product.imageObjectKey())
				.param("now", product.now())
				.param("userId", product.userId())
				.param("organizationId", product.organizationId())
				.param("id", product.productId())
				.update();
	}

	@Override
	public int changeStatus(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now) {
		return updateLifecycle(organizationId, productId, status, null, userId, now);
	}

	@Override
	public int archive(UUID organizationId, UUID productId, UUID userId, OffsetDateTime now) {
		return updateLifecycle(organizationId, productId, "INACTIVE", now, userId, now);
	}

	@Override
	public void replaceCategory(UUID organizationId, UUID productId, UUID categoryId, UUID userId, OffsetDateTime now) {
		jdbcClient.sql("DELETE FROM catalog_product_category WHERE organization_id = :organizationId AND product_id = :productId")
				.param("organizationId", organizationId).param("productId", productId).update();
		if (categoryId != null) {
			jdbcClient.sql("""
					INSERT INTO catalog_product_category (
					    organization_id, product_id, category_id, created_at, created_by
					) VALUES (:organizationId, :productId, :categoryId, :now, :userId)
					""")
					.param("organizationId", organizationId).param("productId", productId)
					.param("categoryId", categoryId).param("now", now).param("userId", userId).update();
		}
	}

	@Override
	public int updateImageKey(UUID organizationId, UUID productId, String imageObjectKey, UUID userId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET image_object_key = :imageObjectKey, updated_at = :now,
				    updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId AND id = :productId
				  AND deleted_at IS NULL AND archived_at IS NULL
				""")
				.param("imageObjectKey", imageObjectKey).param("now", now).param("userId", userId)
				.param("organizationId", organizationId).param("productId", productId).update();
	}

	@Override
	public void replaceTags(UUID organizationId, UUID productId, List<String> tags, UUID userId, OffsetDateTime now) {
		jdbcClient.sql("DELETE FROM catalog_product_tag WHERE organization_id = :organizationId AND product_id = :productId")
				.param("organizationId", organizationId).param("productId", productId).update();
		tags.forEach(tag -> attachTag(organizationId, productId, tag, userId, now));
	}

	private int updateLifecycle(
			UUID organizationId, UUID productId, String status, OffsetDateTime archivedAt,
			UUID userId, OffsetDateTime now
	) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET status = :status, archived_at = :archivedAt, archived_by = :archivedBy,
				    updated_at = :now, updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId AND id = :productId
				  AND deleted_at IS NULL AND archived_at IS NULL
				""")
				.param("status", status).param("archivedAt", archivedAt)
				.param("archivedBy", archivedAt == null ? null : userId)
				.param("now", now).param("userId", userId)
				.param("organizationId", organizationId).param("productId", productId).update();
	}

	private void attachTag(UUID organizationId, UUID productId, String tag, UUID userId, OffsetDateTime now) {
		String normalized = tag.toLowerCase(Locale.ROOT);
		jdbcClient.sql("""
				INSERT INTO catalog_tag (id, organization_id, name, normalized_name, created_at, created_by)
				VALUES (:id, :organizationId, :name, :normalizedName, :now, :userId)
				ON CONFLICT (organization_id, normalized_name) DO NOTHING
				""")
				.param("id", UUID.randomUUID()).param("organizationId", organizationId)
				.param("name", tag).param("normalizedName", normalized)
				.param("now", now).param("userId", userId).update();
		jdbcClient.sql("""
				INSERT INTO catalog_product_tag (organization_id, product_id, tag_id, created_at, created_by)
				SELECT :organizationId, :productId, id, :now, :userId
				FROM catalog_tag WHERE organization_id = :organizationId AND normalized_name = :normalizedName
				ON CONFLICT DO NOTHING
				""")
				.param("organizationId", organizationId).param("productId", productId)
				.param("normalizedName", normalized).param("now", now).param("userId", userId).update();
	}
}
