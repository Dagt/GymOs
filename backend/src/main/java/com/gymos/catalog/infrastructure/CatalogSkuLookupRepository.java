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

	boolean barcodeExists(UUID organizationId, String barcode, UUID excludedProductId) {
		if (excludedProductId == null) {
			return countByBarcode(organizationId, barcode) > 0;
		}
		return countByBarcodeExcludingProduct(organizationId, barcode, excludedProductId) > 0;
	}

	private int countByBarcode(UUID organizationId, String barcode) {
		return jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_product_barcode alias
				WHERE alias.organization_id = :organizationId
				  AND UPPER(BTRIM(alias.barcode)) = UPPER(BTRIM(:barcode))
				  AND alias.active = TRUE
				""")
				.param("organizationId", organizationId)
				.param("barcode", barcode)
				.query(Integer.class)
				.single();
	}

	private int countByBarcodeExcludingProduct(UUID organizationId, String barcode, UUID excludedProductId) {
		return jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_product_barcode alias
				WHERE alias.organization_id = :organizationId
				  AND UPPER(BTRIM(alias.barcode)) = UPPER(BTRIM(:barcode))
				  AND alias.active = TRUE
				  AND alias.product_id <> :excludedProductId
				""")
				.param("organizationId", organizationId)
				.param("barcode", barcode)
				.param("excludedProductId", excludedProductId)
				.query(Integer.class)
				.single();
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
