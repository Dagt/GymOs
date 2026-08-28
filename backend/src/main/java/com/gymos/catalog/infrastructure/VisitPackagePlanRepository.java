package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.query.VisitPackagePlanView;
import com.gymos.catalog.application.port.VisitPackagePlanPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VisitPackagePlanRepository implements VisitPackagePlanPort {
	private final JdbcClient jdbcClient;

	public VisitPackagePlanRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<VisitPackagePlanView> list(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT vpp.id, vpp.product_id, cp.sku, cp.name, cp.description, cp.price_cents,
				       cp.currency, vpp.total_visits, vpp.validity_days, vpp.branch_scope,
				       vpp.self_service_acquisition_enabled, vpp.registration_fee_policy,
				       vpp.status, vpp.version, vpp.created_at, vpp.updated_at
				FROM visit_package_plan vpp
				JOIN catalog_product cp ON cp.id = vpp.product_id
				WHERE vpp.organization_id = :organizationId
				  AND vpp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				  AND (vpp.branch_scope = 'ALL_BRANCHES' OR EXISTS (
				      SELECT 1 FROM visit_package_plan_branch vppb
				      WHERE vppb.organization_id = vpp.organization_id
				        AND vppb.visit_package_plan_id = vpp.id
				        AND vppb.branch_id = :branchId
				  ))
				ORDER BY cp.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((rs, row) -> map(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
						rs.getString("sku"), rs.getString("name"), rs.getString("description"),
						rs.getLong("price_cents"), rs.getString("currency"), rs.getInt("total_visits"),
						rs.getInt("validity_days"), rs.getString("branch_scope"),
						rs.getBoolean("self_service_acquisition_enabled"), rs.getString("registration_fee_policy"),
						rs.getString("status"), rs.getLong("version"),
						rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
						organizationId))
				.list();
	}

	public Optional<VisitPackagePlanView> find(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT vpp.id, vpp.product_id, cp.sku, cp.name, cp.description, cp.price_cents,
				       cp.currency, vpp.total_visits, vpp.validity_days, vpp.branch_scope,
				       vpp.self_service_acquisition_enabled, vpp.registration_fee_policy,
				       vpp.status, vpp.version, vpp.created_at, vpp.updated_at
				FROM visit_package_plan vpp
				JOIN catalog_product cp ON cp.id = vpp.product_id
				WHERE vpp.organization_id = :organizationId
				  AND vpp.id = :planId
				  AND vpp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planId", planId)
				.query((rs, row) -> map(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
						rs.getString("sku"), rs.getString("name"), rs.getString("description"),
						rs.getLong("price_cents"), rs.getString("currency"), rs.getInt("total_visits"),
						rs.getInt("validity_days"), rs.getString("branch_scope"),
						rs.getBoolean("self_service_acquisition_enabled"), rs.getString("registration_fee_policy"),
						rs.getString("status"), rs.getLong("version"),
						rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
						organizationId))
				.optional();
	}

	public void create(
			UUID organizationId, UUID id, UUID productId, int totalVisits, int validityDays,
			String branchScope, boolean selfServiceAcquisitionEnabled, String registrationFeePolicy,
			String status, UUID userId, OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO visit_package_plan (
				    id, organization_id, product_id, total_visits, validity_days, branch_scope,
				    self_service_acquisition_enabled, registration_fee_policy, status,
				    created_at, updated_at, created_by, updated_by, version
				) VALUES (
				    :id, :organizationId, :productId, :totalVisits, :validityDays, :branchScope,
				    :selfServiceAcquisitionEnabled, :registrationFeePolicy, :status,
				    :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", id).param("organizationId", organizationId).param("productId", productId)
				.param("totalVisits", totalVisits).param("validityDays", validityDays)
				.param("branchScope", branchScope)
				.param("selfServiceAcquisitionEnabled", selfServiceAcquisitionEnabled)
				.param("registrationFeePolicy", registrationFeePolicy).param("status", status)
				.param("now", now).param("userId", userId).update();
	}

	public boolean update(
			UUID organizationId, UUID id, int totalVisits, int validityDays, String branchScope,
			boolean selfServiceAcquisitionEnabled, String registrationFeePolicy, String status,
			long expectedVersion, UUID userId, OffsetDateTime now
	) {
		return jdbcClient.sql("""
				UPDATE visit_package_plan
				SET total_visits = :totalVisits, validity_days = :validityDays,
				    branch_scope = :branchScope,
				    self_service_acquisition_enabled = :selfServiceAcquisitionEnabled,
				    registration_fee_policy = :registrationFeePolicy, status = :status,
				    updated_at = :now, updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId AND id = :id
				  AND deleted_at IS NULL AND version = :expectedVersion
				""")
				.param("totalVisits", totalVisits).param("validityDays", validityDays)
				.param("branchScope", branchScope)
				.param("selfServiceAcquisitionEnabled", selfServiceAcquisitionEnabled)
				.param("registrationFeePolicy", registrationFeePolicy).param("status", status)
				.param("now", now).param("userId", userId).param("organizationId", organizationId)
				.param("id", id).param("expectedVersion", expectedVersion).update() == 1;
	}

	public void replaceBranches(UUID organizationId, UUID planId, List<UUID> branchIds, UUID userId) {
		jdbcClient.sql("DELETE FROM visit_package_plan_branch WHERE organization_id=:organizationId AND visit_package_plan_id=:planId")
				.param("organizationId", organizationId).param("planId", planId).update();
		for (UUID branchId : branchIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO visit_package_plan_branch (
					    visit_package_plan_id, branch_id, organization_id, created_at, created_by
					) VALUES (:planId, :branchId, :organizationId, now(), :userId)
					""")
					.param("planId", planId).param("branchId", branchId)
					.param("organizationId", organizationId).param("userId", userId).update();
		}
	}

	public void replaceBenefits(UUID organizationId, UUID planId, List<UUID> benefitIds, UUID userId) {
		jdbcClient.sql("DELETE FROM visit_package_plan_benefit WHERE organization_id=:organizationId AND visit_package_plan_id=:planId")
				.param("organizationId", organizationId).param("planId", planId).update();
		for (UUID benefitId : benefitIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO visit_package_plan_benefit (
					    visit_package_plan_id, benefit_id, organization_id, created_at, created_by
					) VALUES (:planId, :benefitId, :organizationId, now(), :userId)
					""")
					.param("planId", planId).param("benefitId", benefitId)
					.param("organizationId", organizationId).param("userId", userId).update();
		}
	}

	public boolean branchesExist(UUID organizationId, List<UUID> branchIds) {
		if (branchIds.isEmpty()) return true;
		List<UUID> ids = branchIds.stream().distinct().toList();
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*) FROM branch
				WHERE organization_id=:organizationId AND id IN (:ids)
				  AND status='ACTIVE' AND deleted_at IS NULL
				""").param("organizationId", organizationId).param("ids", ids).query(Integer.class).single();
		return count == ids.size();
	}

	public List<UUID> activeBranchIds(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id FROM branch
				WHERE organization_id=:organizationId AND status='ACTIVE' AND deleted_at IS NULL
				ORDER BY id
				""").param("organizationId", organizationId).query(UUID.class).list();
	}

	public boolean accessBenefitsExist(UUID organizationId, List<UUID> benefitIds) {
		if (benefitIds.isEmpty()) return true;
		List<UUID> ids = benefitIds.stream().distinct().toList();
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*) FROM benefit
				WHERE organization_id=:organizationId AND id IN (:ids)
				  AND benefit_type='ACCESS' AND status='ACTIVE' AND deleted_at IS NULL
				""").param("organizationId", organizationId).param("ids", ids).query(Integer.class).single();
		return count == ids.size();
	}

	@Override
	public boolean configuredProduct(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1 FROM visit_package_plan
				    WHERE organization_id=:organizationId AND product_id=:productId AND deleted_at IS NULL
				)
				""").param("organizationId", organizationId).param("productId", productId)
				.query(Boolean.class).single();
	}

	private VisitPackagePlanView map(
			UUID id, UUID productId, String sku, String name, String description, long priceCents,
			String currency, int totalVisits, int validityDays, String branchScope,
			boolean selfServiceAcquisitionEnabled, String registrationFeePolicy, String status,
			long version, OffsetDateTime createdAt, OffsetDateTime updatedAt, UUID organizationId
	) {
		return new VisitPackagePlanView(
				id, productId, sku, name, description, priceCents, currency, totalVisits, validityDays,
				branchScope, branchIds(organizationId, id), benefitIds(organizationId, id),
				selfServiceAcquisitionEnabled, registrationFeePolicy, status, version, createdAt, updatedAt);
	}

	private List<UUID> branchIds(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT branch_id FROM visit_package_plan_branch
				WHERE organization_id=:organizationId AND visit_package_plan_id=:planId ORDER BY branch_id
				""").param("organizationId", organizationId).param("planId", planId).query(UUID.class).list();
	}

	private List<UUID> benefitIds(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT benefit_id FROM visit_package_plan_benefit
				WHERE organization_id=:organizationId AND visit_package_plan_id=:planId ORDER BY benefit_id
				""").param("organizationId", organizationId).param("planId", planId).query(UUID.class).list();
	}
}
