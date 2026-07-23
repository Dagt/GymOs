package com.gymos.sales.infrastructure;

import com.gymos.sales.model.ProductForSale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesCatalogLookupRepository {
	private final JdbcClient jdbcClient;

	SalesCatalogLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<ProductForSale> productForSale(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT id, name, product_type, price_cents, currency
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((resultSet, rowNumber) -> new ProductForSale(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("product_type"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency")
				))
				.optional();
	}
}
