package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogDependencyRepository implements CatalogDependencyQueryPort {
	private final JdbcClient jdbcClient;

	CatalogDependencyRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public boolean hasInventoryMovements(UUID organizationId, UUID productId) {
		return count("""
				SELECT COUNT(*)
				FROM inventory_movement
				WHERE organization_id = :organizationId
				  AND product_id = :productId
				""", organizationId, productId) > 0;
	}

	@Override
	public boolean hasActiveMembershipPlan(UUID organizationId, UUID productId) {
		return count("""
				SELECT COUNT(*)
				FROM membership_plan
				WHERE organization_id = :organizationId
				  AND product_id = :productId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""", organizationId, productId) > 0;
	}

	private int count(String sql, UUID organizationId, UUID productId) {
		return jdbcClient.sql(sql)
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(Integer.class)
				.single();
	}
}
