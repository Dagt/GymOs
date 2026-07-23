package com.gymos.sales.infrastructure;

import com.gymos.sales.model.MembershipPlanForSale;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesMembershipLookupRepository {
	private final JdbcClient jdbcClient;

	SalesMembershipLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<MembershipPlanForSale> membershipPlansForSale(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT DISTINCT mp.id AS plan_id, si.product_id, mp.duration_days, mp.grace_days
				FROM sale_item si
				JOIN membership_plan mp ON mp.product_id = si.product_id
				WHERE si.organization_id = :organizationId
				  AND si.sale_id = :saleId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new MembershipPlanForSale(
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days")
				))
				.list();
	}

	boolean membershipExistsForSalePlan(UUID organizationId, UUID saleId, UUID planId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND plan_id = :planId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.param("planId", planId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
		return jdbcClient.sql("""
				SELECT MAX(ends_on)
				FROM membership
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND plan_id = :planId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("planId", planId)
				.query(LocalDate.class)
				.optional();
	}
}
