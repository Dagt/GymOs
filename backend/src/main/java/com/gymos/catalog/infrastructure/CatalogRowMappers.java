package com.gymos.catalog.infrastructure;

import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class CatalogRowMappers {
	private CatalogRowMappers() {
	}

	static RowMapper<ProductSummary> productSummary() {
		return (resultSet, rowNumber) -> new ProductSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("sku"),
				resultSet.getString("name"),
				resultSet.getString("description"),
				resultSet.getString("product_type"),
				resultSet.getLong("price_cents"),
				resultSet.getString("currency"),
				resultSet.getBoolean("taxable"),
				resultSet.getString("status"),
				resultSet.getString("barcode"),
				resultSet.getString("brand"),
				resultSet.getString("presentation"),
				resultSet.getString("unit_of_measure"),
				resultSet.getBoolean("inventory_tracked"),
				resultSet.getInt("display_order"),
				resultSet.getString("image_object_key"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	static RowMapper<PlanSummary> planSummary() {
		return (resultSet, rowNumber) -> new PlanSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("product_id", UUID.class),
				resultSet.getString("sku"),
				resultSet.getString("name"),
				resultSet.getLong("price_cents"),
				resultSet.getString("currency"),
				resultSet.getInt("duration_days"),
				resultSet.getInt("grace_days"),
				resultSet.getString("benefits_json"),
				resultSet.getBoolean("multi_branch_access"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}
}
