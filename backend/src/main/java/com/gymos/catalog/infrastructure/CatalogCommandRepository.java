package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogPlanCommandPort;
import com.gymos.catalog.application.port.CatalogPlanCommandPort.PlanMutation;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogCommandRepository implements CatalogProductCommandPort, CatalogPlanCommandPort {
	private final JdbcClient jdbcClient;

	public CatalogCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void create(ProductMutation mutation) {
		createProduct(new ProductRecord(
				mutation.productId(), mutation.organizationId(), mutation.sku(), mutation.name(),
				mutation.description(), mutation.productType(), mutation.priceCents(), mutation.currency(),
				mutation.taxable(), mutation.status(), mutation.barcode(), mutation.brand(), mutation.presentation(),
				mutation.unitOfMeasure(), mutation.inventoryTracked(), mutation.displayOrder(),
				mutation.imageObjectKey(), mutation.now(), mutation.userId()
		));
	}

	@Override
	public int update(ProductMutation mutation) {
		return updateProduct(new ProductUpdateRecord(
				mutation.organizationId(), mutation.productId(), mutation.sku(), mutation.name(),
				mutation.description(), mutation.productType(), mutation.priceCents(), mutation.currency(),
				mutation.taxable(), mutation.status(), mutation.barcode(), mutation.brand(), mutation.presentation(),
				mutation.unitOfMeasure(), mutation.inventoryTracked(), mutation.displayOrder(),
				mutation.imageObjectKey(), mutation.now(), mutation.userId()
		));
	}

	@Override
	public int changeStatus(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now) {
		return changeProductStatus(organizationId, productId, status, userId, now);
	}

	@Override
	public int archive(UUID organizationId, UUID productId, UUID userId, OffsetDateTime now) {
		return archiveProduct(organizationId, productId, userId, now);
	}

	@Override
	public void replaceCategory(
			UUID organizationId,
			UUID productId,
			UUID categoryId,
			UUID userId,
			OffsetDateTime now
	) {
		jdbcClient.sql("""
				DELETE FROM catalog_product_category
				WHERE organization_id = :organizationId
				  AND product_id = :productId
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.update();
		if (categoryId == null) {
			return;
		}
		jdbcClient.sql("""
				INSERT INTO catalog_product_category (
				    organization_id, product_id, category_id, created_at, created_by
				)
				VALUES (:organizationId, :productId, :categoryId, :now, :userId)
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.param("categoryId", categoryId)
				.param("now", now)
				.param("userId", userId)
				.update();
	}

	@Override
	public void create(PlanMutation mutation) {
		createPlan(new PlanRecord(
				mutation.planId(), mutation.organizationId(), mutation.productId(), mutation.durationDays(),
				mutation.graceDays(), mutation.benefitsJson(), mutation.multiBranchAccess(), mutation.status(),
				mutation.now(), mutation.userId()
		));
	}

	@Override
	public void update(PlanMutation mutation) {
		updatePlan(new PlanUpdateRecord(
				mutation.organizationId(), mutation.planId(), mutation.durationDays(), mutation.graceDays(),
				mutation.benefitsJson(), mutation.multiBranchAccess(), mutation.status(),
				mutation.now(), mutation.userId()
		));
	}

	public void createProduct(ProductRecord record) {
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
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", record.id())
				.param("organizationId", record.organizationId())
				.param("sku", record.sku())
				.param("name", record.name())
				.param("description", record.description())
				.param("productType", record.productType())
				.param("priceCents", record.priceCents())
				.param("currency", record.currency())
				.param("taxable", record.taxable())
				.param("status", record.status())
				.param("barcode", record.barcode())
				.param("brand", record.brand())
				.param("presentation", record.presentation())
				.param("unitOfMeasure", record.unitOfMeasure())
				.param("inventoryTracked", record.inventoryTracked())
				.param("displayOrder", record.displayOrder())
				.param("imageObjectKey", record.imageObjectKey())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
	}

	public int updateProduct(ProductUpdateRecord record) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET sku = :sku,
				    name = :name,
				    description = :description,
				    product_type = :productType,
				    price_cents = :priceCents,
				    currency = :currency,
				    taxable = :taxable,
				    status = :status,
				    barcode = :barcode,
				    brand = :brand,
				    presentation = :presentation,
				    unit_of_measure = :unitOfMeasure,
				    inventory_tracked = :inventoryTracked,
				    display_order = :displayOrder,
				    image_object_key = :imageObjectKey,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""")
				.param("sku", record.sku())
				.param("name", record.name())
				.param("description", record.description())
				.param("productType", record.productType())
				.param("priceCents", record.priceCents())
				.param("currency", record.currency())
				.param("taxable", record.taxable())
				.param("status", record.status())
				.param("barcode", record.barcode())
				.param("brand", record.brand())
				.param("presentation", record.presentation())
				.param("unitOfMeasure", record.unitOfMeasure())
				.param("inventoryTracked", record.inventoryTracked())
				.param("displayOrder", record.displayOrder())
				.param("imageObjectKey", record.imageObjectKey())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("organizationId", record.organizationId())
				.param("productId", record.productId())
				.update();
	}

	public int changeProductStatus(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""")
				.param("status", status)
				.param("updatedAt", now)
				.param("updatedBy", userId)
				.param("organizationId", organizationId)
				.param("productId", productId)
				.update();
	}

	public int archiveProduct(UUID organizationId, UUID productId, UUID userId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE catalog_product
				SET status = 'INACTIVE',
				    archived_at = :archivedAt,
				    archived_by = :archivedBy,
				    updated_at = :archivedAt,
				    updated_by = :archivedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""")
				.param("archivedAt", now)
				.param("archivedBy", userId)
				.param("organizationId", organizationId)
				.param("productId", productId)
				.update();
	}

	public void createPlan(PlanRecord record) {
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				    id, organization_id, product_id, duration_days, grace_days, benefits_json,
				    multi_branch_access, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    :multiBranchAccess, :status, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", record.id())
				.param("organizationId", record.organizationId())
				.param("productId", record.productId())
				.param("durationDays", record.durationDays())
				.param("graceDays", record.graceDays())
				.param("benefitsJson", record.benefitsJson())
				.param("multiBranchAccess", record.multiBranchAccess())
				.param("status", record.status())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
	}

	public void updatePlan(PlanUpdateRecord record) {
		jdbcClient.sql("""
				UPDATE membership_plan
				SET duration_days = :durationDays,
				    grace_days = :graceDays,
				    benefits_json = CAST(:benefitsJson AS jsonb),
				    multi_branch_access = :multiBranchAccess,
				    status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :planId
				  AND deleted_at IS NULL
				""")
				.param("durationDays", record.durationDays())
				.param("graceDays", record.graceDays())
				.param("benefitsJson", record.benefitsJson())
				.param("multiBranchAccess", record.multiBranchAccess())
				.param("status", record.status())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("organizationId", record.organizationId())
				.param("planId", record.planId())
				.update();
	}

	public record ProductRecord(
			UUID id, UUID organizationId, String sku, String name, String description,
			String productType, long priceCents, String currency, boolean taxable, String status,
			String barcode, String brand, String presentation, String unitOfMeasure,
			boolean inventoryTracked, int displayOrder, String imageObjectKey,
			OffsetDateTime now, UUID userId
	) {
	}

	public record ProductUpdateRecord(
			UUID organizationId, UUID productId, String sku, String name, String description,
			String productType, long priceCents, String currency, boolean taxable, String status,
			String barcode, String brand, String presentation, String unitOfMeasure,
			boolean inventoryTracked, int displayOrder, String imageObjectKey,
			OffsetDateTime now, UUID userId
	) {
	}

	public record PlanRecord(
			UUID id, UUID organizationId, UUID productId, int durationDays, int graceDays,
			String benefitsJson, boolean multiBranchAccess, String status,
			OffsetDateTime now, UUID userId
	) {
	}

	public record PlanUpdateRecord(
			UUID organizationId, UUID planId, int durationDays, int graceDays,
			String benefitsJson, boolean multiBranchAccess, String status,
			OffsetDateTime now, UUID userId
	) {
	}
}
