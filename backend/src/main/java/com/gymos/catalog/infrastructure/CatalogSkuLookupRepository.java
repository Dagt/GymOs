package com.gymos.catalog.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogSkuLookupRepository {
	private final JdbcClient jdbcClient;

	CatalogSkuLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	boolean exists(UUID organizationId, String sku, UUID excludedProductId) {
		if (excludedProductId == null) {
			return countBySku(organizationId, sku) > 0;
		}
		return countBySkuExcludingProduct(organizationId, sku, excludedProductId) > 0;
	}

	private int countBySku(UUID organizationId, String sku) {
		return jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND sku = :sku
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("sku", sku)
				.query(Integer.class)
				.single();
	}

	private int countBySkuExcludingProduct(UUID organizationId, String sku, UUID excludedProductId) {
		return jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND sku = :sku
				  AND deleted_at IS NULL
				  AND id <> :excludedProductId
				""")
				.param("organizationId", organizationId)
				.param("sku", sku)
				.param("excludedProductId", excludedProductId)
				.query(Integer.class)
				.single();
	}
}
