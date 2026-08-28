package com.gymos.visitpackages.infrastructure;

import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VisitPackageEntitlementRepository implements VisitPackageEntitlementPort {
	private final JdbcClient jdbcClient;

	public VisitPackageEntitlementRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public boolean lockActiveClient(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT status FROM client
				WHERE organization_id = :organizationId AND id = :clientId
				  AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(String.class)
				.optional()
				.filter("ACTIVE"::equals)
				.isPresent();
	}

	@Override
	public void expireDue(UUID organizationId, UUID clientId, LocalDate today, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET status = CASE WHEN status = 'ACTIVE' THEN 'EXPIRED' ELSE 'CANCELLED' END,
				    updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId AND client_id = :clientId
				  AND deleted_at IS NULL
				  AND ((status = 'ACTIVE' AND valid_until < :today)
				       OR (status = 'PENDING_PAYMENT' AND pending_expires_at < :now))
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("today", today).param("now", now).update();
	}

	@Override
	public boolean hasActiveLegacyPass(UUID organizationId, UUID clientId, LocalDate today) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1 FROM visit_pass
				    WHERE organization_id = :organizationId AND client_id = :clientId
				      AND status = 'ACTIVE' AND remaining_visits > 0
				      AND valid_from <= :today AND (valid_until IS NULL OR valid_until >= :today)
				)
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("today", today).query(Boolean.class).single();
	}

	@Override
	public Optional<VisitPackageEntitlementView> open(UUID organizationId, UUID clientId) {
		return query("""
				entitlement.organization_id = :organizationId
				AND entitlement.client_id = :clientId
				AND entitlement.status IN ('PENDING_PAYMENT', 'ACTIVE')
				""", organizationId, clientId, null, null);
	}

	@Override
	public Optional<VisitPackageEntitlementView> bySale(UUID organizationId, UUID saleId) {
		return query("""
				entitlement.organization_id = :organizationId
				AND entitlement.sale_id = :saleId
				""", organizationId, null, saleId, null);
	}

	@Override
	public Optional<VisitPackageEntitlementView> byIdempotencyKey(
			UUID organizationId, UUID clientId, String idempotencyKey
	) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
		return query("""
				entitlement.organization_id = :organizationId
				AND entitlement.client_id = :clientId
				AND purchase_sale.idempotency_key = :idempotencyKey
				""", organizationId, clientId, null, idempotencyKey.trim());
	}

	@Override
	public UUID createPending(
			UUID organizationId, UUID homeBranchId, UUID clientId, UUID packagePlanId,
			UUID saleId, String packageName, int totalVisits, int validityDays,
			OffsetDateTime pendingExpiresAt, List<UUID> branchIds, List<UUID> benefitIds,
			OffsetDateTime now
	) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO visit_package_entitlement (
				    id, organization_id, home_branch_id, client_id, visit_package_plan_id,
				    sale_id, status, total_visits, remaining_visits, validity_days,
				    pending_expires_at, created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :homeBranchId, :clientId, :packagePlanId,
				    :saleId, 'PENDING_PAYMENT', :totalVisits, :totalVisits, :validityDays,
				    :pendingExpiresAt, :now, :now, 0
				)
				""")
				.param("id", id).param("organizationId", organizationId)
				.param("homeBranchId", homeBranchId).param("clientId", clientId)
				.param("packagePlanId", packagePlanId).param("saleId", saleId)
				.param("totalVisits", totalVisits).param("validityDays", validityDays)
				.param("pendingExpiresAt", pendingExpiresAt).param("now", now).update();
		for (UUID branchId : branchIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO visit_package_entitlement_branch (
					    entitlement_id, branch_id, organization_id, created_at
					) VALUES (:entitlementId, :branchId, :organizationId, :now)
					""")
					.param("entitlementId", id).param("branchId", branchId)
					.param("organizationId", organizationId).param("now", now).update();
		}
		for (UUID benefitId : benefitIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO visit_package_entitlement_benefit (
					    entitlement_id, benefit_id, organization_id, created_at
					) VALUES (:entitlementId, :benefitId, :organizationId, :now)
					""")
					.param("entitlementId", id).param("benefitId", benefitId)
					.param("organizationId", organizationId).param("now", now).update();
		}
		return id;
	}

	@Override
	public boolean activateBySale(UUID organizationId, UUID saleId, LocalDate validFrom, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET status = 'ACTIVE', valid_from = :validFrom,
				    valid_until = :validFrom + (validity_days - 1), activated_at = :now,
				    pending_expires_at = NULL, updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId AND sale_id = :saleId
				  AND status = 'PENDING_PAYMENT' AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("saleId", saleId)
				.param("validFrom", validFrom).param("now", now).update() == 1;
	}

	@Override
	public Optional<ActivationDefinition> activationDefinition(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT id, validity_days, status FROM visit_package_entitlement
				WHERE organization_id = :organizationId AND sale_id = :saleId AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("saleId", saleId)
				.query((rs, row) -> new ActivationDefinition(
						rs.getObject("id", UUID.class), rs.getInt("validity_days"), rs.getString("status")))
				.optional();
	}

	@Override
	public LocalDate businessDate(UUID organizationId, UUID branchId, OffsetDateTime occurredAt) {
		String timezone = jdbcClient.sql("""
				SELECT timezone FROM branch
				WHERE organization_id=:organizationId AND id=:branchId AND deleted_at IS NULL
				""").param("organizationId", organizationId).param("branchId", branchId)
				.query(String.class).single();
		return occurredAt.atZoneSameInstant(ZoneId.of(timezone)).toLocalDate();
	}

	@Override
	public Optional<VisitPackageAccessResult> consumeForAccess(
			UUID organizationId, UUID branchId, UUID clientId, List<UUID> requiredBenefitIds,
			UUID accessDeviceId, String idempotencyKey, LocalDate businessDate, OffsetDateTime occurredAt
	) {
		jdbcClient.sql("""
				SELECT id FROM client
				WHERE organization_id=:organizationId AND id=:clientId AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.query(UUID.class).optional();
		Optional<VisitPackageAccessResult> existing = consumptionByKey(organizationId, idempotencyKey);
		if (existing.isPresent()) return existing;

		jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET status='EXPIRED', updated_at=:occurredAt, version=version+1
				WHERE organization_id=:organizationId AND client_id=:clientId
				  AND status='ACTIVE' AND valid_until < :businessDate AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("businessDate", businessDate).param("occurredAt", occurredAt).update();

		String benefitPredicate = requiredBenefitIds == null || requiredBenefitIds.isEmpty()
				? ""
				: """
				  AND EXISTS (
				      SELECT 1 FROM visit_package_entitlement_benefit entitlement_benefit
				      WHERE entitlement_benefit.organization_id=entitlement.organization_id
				        AND entitlement_benefit.entitlement_id=entitlement.id
				        AND entitlement_benefit.benefit_id IN (:requiredBenefitIds)
				  )
				""";
		JdbcClient.StatementSpec candidateStatement = jdbcClient.sql("""
				SELECT entitlement.id
				FROM visit_package_entitlement entitlement
				JOIN visit_package_entitlement_branch entitlement_branch
				  ON entitlement_branch.organization_id=entitlement.organization_id
				 AND entitlement_branch.entitlement_id=entitlement.id
				WHERE entitlement.organization_id=:organizationId
				  AND entitlement.client_id=:clientId
				  AND entitlement_branch.branch_id=:branchId
				  AND entitlement.status='ACTIVE'
				  AND entitlement.remaining_visits > 0
				  AND entitlement.valid_from <= :businessDate
				  AND entitlement.valid_until >= :businessDate
				  AND entitlement.deleted_at IS NULL
				""" + benefitPredicate + " FOR UPDATE OF entitlement LIMIT 1")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("branchId", branchId).param("businessDate", businessDate);
		if (requiredBenefitIds != null && !requiredBenefitIds.isEmpty()) {
			candidateStatement = candidateStatement.param("requiredBenefitIds", requiredBenefitIds);
		}
		Optional<UUID> entitlementId = candidateStatement.query(UUID.class).optional();
		if (entitlementId.isEmpty()) return Optional.empty();

		int remainingAfter = jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET remaining_visits=remaining_visits-1,
				    status=CASE WHEN remaining_visits-1=0 THEN 'CONSUMED' ELSE status END,
				    updated_at=:occurredAt, version=version+1
				WHERE organization_id=:organizationId AND id=:entitlementId
				  AND status='ACTIVE' AND remaining_visits > 0
				RETURNING remaining_visits
				""")
				.param("organizationId", organizationId).param("entitlementId", entitlementId.get())
				.param("occurredAt", occurredAt).query(Integer.class).single();
		UUID consumptionId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO visit_package_consumption (
				    id, organization_id, entitlement_id, branch_id, access_device_id,
				    idempotency_key, status, remaining_after, occurred_at, created_at
				) VALUES (
				    :id, :organizationId, :entitlementId, :branchId, :accessDeviceId,
				    :idempotencyKey, 'CONSUMED', :remainingAfter, :occurredAt, :occurredAt
				)
				""")
				.param("id", consumptionId).param("organizationId", organizationId)
				.param("entitlementId", entitlementId.get()).param("branchId", branchId)
				.param("accessDeviceId", accessDeviceId).param("idempotencyKey", idempotencyKey)
				.param("remainingAfter", remainingAfter).param("occurredAt", occurredAt).update();
		return consumptionByKey(organizationId, idempotencyKey);
	}

	@Override
	public boolean linkAccessLog(UUID organizationId, UUID consumptionId, UUID accessLogId) {
		return jdbcClient.sql("""
				UPDATE visit_package_consumption
				SET access_log_id=:accessLogId
				WHERE organization_id=:organizationId AND id=:consumptionId
				  AND (access_log_id IS NULL OR access_log_id=:accessLogId)
				""")
				.param("organizationId", organizationId).param("consumptionId", consumptionId)
				.param("accessLogId", accessLogId).update() == 1;
	}

	private Optional<VisitPackageAccessResult> consumptionByKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT consumption.id, consumption.entitlement_id, entitlement.client_id,
				       consumption.branch_id, consumption.access_device_id, product.name AS package_name,
				       consumption.remaining_after, consumption.access_log_id
				FROM visit_package_consumption consumption
				JOIN visit_package_entitlement entitlement ON entitlement.id=consumption.entitlement_id
				JOIN visit_package_plan plan ON plan.id=entitlement.visit_package_plan_id
				JOIN catalog_product product ON product.id=plan.product_id
				WHERE consumption.organization_id=:organizationId
				  AND consumption.idempotency_key=:idempotencyKey
				""")
				.param("organizationId", organizationId).param("idempotencyKey", idempotencyKey)
				.query((rs, row) -> new VisitPackageAccessResult(
						rs.getObject("id", UUID.class), rs.getObject("entitlement_id", UUID.class),
						rs.getObject("client_id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getObject("access_device_id", UUID.class), rs.getString("package_name"),
						rs.getInt("remaining_after"), rs.getObject("access_log_id", UUID.class)))
				.optional();
	}

	private Optional<VisitPackageEntitlementView> query(
			String predicate, UUID organizationId, UUID clientId, UUID saleId, String idempotencyKey
	) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				SELECT entitlement.id, entitlement.client_id, entitlement.visit_package_plan_id,
				       entitlement.sale_id, product.name AS package_name, entitlement.status,
				       entitlement.total_visits, entitlement.remaining_visits,
				       entitlement.valid_from, entitlement.valid_until, entitlement.pending_expires_at,
				       entitlement.created_at, entitlement.updated_at
				FROM visit_package_entitlement entitlement
				JOIN visit_package_plan plan
				  ON plan.id = entitlement.visit_package_plan_id
				 AND plan.organization_id = entitlement.organization_id
				JOIN catalog_product product
				  ON product.id = plan.product_id
				 AND product.organization_id = entitlement.organization_id
				JOIN sale purchase_sale
				  ON purchase_sale.id = entitlement.sale_id
				 AND purchase_sale.organization_id = entitlement.organization_id
				WHERE """ + predicate + " AND entitlement.deleted_at IS NULL ORDER BY entitlement.created_at DESC LIMIT 1")
				.param("organizationId", organizationId);
		if (clientId != null) statement = statement.param("clientId", clientId);
		if (saleId != null) statement = statement.param("saleId", saleId);
		if (idempotencyKey != null) statement = statement.param("idempotencyKey", idempotencyKey);
		return statement.query((rs, row) -> {
			UUID entitlementId = rs.getObject("id", UUID.class);
			UUID resolvedClientId = rs.getObject("client_id", UUID.class);
			UUID packagePlanId = rs.getObject("visit_package_plan_id", UUID.class);
			UUID resolvedSaleId = rs.getObject("sale_id", UUID.class);
			String packageName = rs.getString("package_name");
			String status = rs.getString("status");
			int totalVisits = rs.getInt("total_visits");
			int remainingVisits = rs.getInt("remaining_visits");
			LocalDate validFrom = rs.getObject("valid_from", LocalDate.class);
			LocalDate validUntil = rs.getObject("valid_until", LocalDate.class);
			OffsetDateTime pendingExpiresAt = rs.getObject("pending_expires_at", OffsetDateTime.class);
			OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
			OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
			// Read the complete row before issuing the two snapshot queries. Some JDBC drivers
			// close the active ResultSet when another statement uses the same transaction connection.
			List<UUID> resolvedBranchIds = branchIds(organizationId, entitlementId);
			List<UUID> resolvedBenefitIds = benefitIds(organizationId, entitlementId);
			return new VisitPackageEntitlementView(
					entitlementId, resolvedClientId, packagePlanId, resolvedSaleId,
					packageName, status, totalVisits, remainingVisits, validFrom, validUntil,
					pendingExpiresAt, resolvedBranchIds, resolvedBenefitIds, createdAt, updatedAt);
		}).optional();
	}

	private List<UUID> branchIds(UUID organizationId, UUID entitlementId) {
		return jdbcClient.sql("""
				SELECT branch_id FROM visit_package_entitlement_branch
				WHERE organization_id=:organizationId AND entitlement_id=:entitlementId ORDER BY branch_id
				""").param("organizationId", organizationId).param("entitlementId", entitlementId)
				.query(UUID.class).list();
	}

	private List<UUID> benefitIds(UUID organizationId, UUID entitlementId) {
		return jdbcClient.sql("""
				SELECT benefit_id FROM visit_package_entitlement_benefit
				WHERE organization_id=:organizationId AND entitlement_id=:entitlementId ORDER BY benefit_id
				""").param("organizationId", organizationId).param("entitlementId", entitlementId)
				.query(UUID.class).list();
	}
}
