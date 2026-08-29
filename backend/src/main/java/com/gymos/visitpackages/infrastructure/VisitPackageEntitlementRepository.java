package com.gymos.visitpackages.infrastructure;

import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.common.api.BusinessException;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageBenefitView;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageConsumptionView;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort.BenefitCoverage;
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
				""", organizationId, clientId, null, null, null);
	}

	@Override
	public List<VisitPackageEntitlementView> history(UUID organizationId, UUID clientId, int limit) {
		List<UUID> ids = jdbcClient.sql("""
				SELECT id FROM visit_package_entitlement
				WHERE organization_id=:organizationId AND client_id=:clientId
				  AND deleted_at IS NULL
				ORDER BY created_at DESC, id DESC
				LIMIT :limit
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("limit", limit).query(UUID.class).list();
		return ids.stream()
				.map(id -> query("""
						entitlement.organization_id = :organizationId
						AND entitlement.id = :entitlementId
						""", organizationId, null, null, null, id).orElseThrow())
				.toList();
	}

	@Override
	public Optional<VisitPackageEntitlementView> bySale(UUID organizationId, UUID saleId) {
		return query("""
				entitlement.organization_id = :organizationId
				AND entitlement.sale_id = :saleId
				""", organizationId, null, saleId, null, null);
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
				""", organizationId, clientId, null, idempotencyKey.trim(), null);
	}

	@Override
	public UUID createPending(
			UUID organizationId, UUID homeBranchId, UUID clientId, UUID packagePlanId,
			UUID saleId, String packageName, int totalVisits, int validityDays,
			OffsetDateTime pendingExpiresAt, List<UUID> branchIds, List<BenefitCoverage> benefitCoverages,
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
		for (BenefitCoverage coverage : benefitCoverages.stream()
				.collect(java.util.stream.Collectors.toMap(
						BenefitCoverage::benefitId, value -> value, (first, ignored) -> first,
						java.util.LinkedHashMap::new)).values()) {
			UUID benefitId = coverage.benefitId();
			jdbcClient.sql("""
					INSERT INTO visit_package_entitlement_benefit (
					    entitlement_id, benefit_id, organization_id, usage_mode, created_at
					) VALUES (:entitlementId, :benefitId, :organizationId, :usageMode, :now)
					""")
					.param("entitlementId", id).param("benefitId", benefitId)
					.param("usageMode", coverage.usageMode().name())
					.param("organizationId", organizationId).param("now", now).update();
			for (UUID branchId : coverage.branchIds().stream().distinct().toList()) {
				if (!branchIds.contains(branchId)) {
					throw new IllegalArgumentException("Benefit coverage must be contained in package branches");
				}
				jdbcClient.sql("""
						INSERT INTO visit_package_entitlement_benefit_branch (
						    entitlement_id, benefit_id, branch_id, organization_id, usage_mode, created_at
						) VALUES (
						    :entitlementId, :benefitId, :branchId, :organizationId, :usageMode, :now
						)
						""")
						.param("entitlementId", id).param("benefitId", benefitId)
						.param("branchId", branchId).param("organizationId", organizationId)
						.param("usageMode", coverage.usageMode().name())
						.param("now", now).update();
			}
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
	public Optional<VisitPackageAccessResult> authorizeForAccess(
			UUID organizationId, UUID branchId, UUID clientId, List<UUID> requiredBenefitIds,
			UUID accessDeviceId, String idempotencyKey, LocalDate businessDate, OffsetDateTime occurredAt
	) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
				.param("lockKey", organizationId + ":visit-package-access:" + idempotencyKey)
				.query((rs, row) -> Boolean.TRUE).single();
		jdbcClient.sql("""
				SELECT id FROM client
				WHERE organization_id=:organizationId AND id=:clientId AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.query(UUID.class).optional();
		Optional<VisitPackageAccessResult> existing = usageByKey(organizationId, idempotencyKey);
		if (existing.isPresent()) {
			VisitPackageAccessResult previous = existing.get();
			if (!previous.clientId().equals(clientId)
					|| !previous.branchId().equals(branchId)
					|| !java.util.Objects.equals(previous.accessDeviceId(), accessDeviceId)) {
				throw new BusinessException(
						"VISIT_PACKAGE_ACCESS_KEY_REUSED",
						"El identificador del evento ya corresponde a otro acceso.");
			}
			return existing;
		}

		jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET status='EXPIRED', updated_at=:occurredAt, version=version+1
				WHERE organization_id=:organizationId AND client_id=:clientId
				  AND status='ACTIVE' AND valid_until < :businessDate AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("businessDate", businessDate).param("occurredAt", occurredAt).update();

		boolean benefitRequired = requiredBenefitIds != null && !requiredBenefitIds.isEmpty();
		String benefitPredicate = benefitRequired
				? " AND entitlement_benefit.benefit_id IN (:requiredBenefitIds) "
				: """
				  AND entitlement_benefit.usage_mode = 'CONSUMES_VISIT'
				""";
		JdbcClient.StatementSpec candidateStatement = jdbcClient.sql("""
				SELECT entitlement.id AS entitlement_id,
				       entitlement_benefit.benefit_id,
				       entitlement_benefit.usage_mode,
				       entitlement.remaining_visits
				FROM visit_package_entitlement entitlement
				JOIN visit_package_entitlement_branch entitlement_branch
				  ON entitlement_branch.organization_id=entitlement.organization_id
				 AND entitlement_branch.entitlement_id=entitlement.id
				JOIN visit_package_entitlement_benefit_branch entitlement_benefit
				  ON entitlement_benefit.organization_id=entitlement.organization_id
				 AND entitlement_benefit.entitlement_id=entitlement.id
				 AND entitlement_benefit.branch_id=entitlement_branch.branch_id
				WHERE entitlement.organization_id=:organizationId
				  AND entitlement.client_id=:clientId
				  AND entitlement_branch.branch_id=:branchId
				  AND entitlement.status='ACTIVE'
				  AND entitlement.valid_from <= :businessDate
				  AND entitlement.valid_until >= :businessDate
				  AND entitlement.deleted_at IS NULL
				  AND (
				      entitlement_benefit.usage_mode = 'INCLUDED_WITH_VISIT'
				      OR entitlement.remaining_visits > 0
				  )
				""" + benefitPredicate + """
				ORDER BY CASE entitlement_benefit.usage_mode
				           WHEN 'CONSUMES_VISIT' THEN 0 ELSE 1 END,
				         entitlement_benefit.benefit_id
				FOR UPDATE OF entitlement
				LIMIT 1
				""")
				.param("organizationId", organizationId).param("clientId", clientId)
				.param("branchId", branchId).param("businessDate", businessDate);
		if (benefitRequired) {
			candidateStatement = candidateStatement.param("requiredBenefitIds", requiredBenefitIds);
		}
		Optional<AccessCandidate> candidate = candidateStatement.query((rs, row) -> new AccessCandidate(
				rs.getObject("entitlement_id", UUID.class),
				rs.getObject("benefit_id", UUID.class),
				VisitPackageBenefitUsageMode.valueOf(rs.getString("usage_mode")),
				rs.getInt("remaining_visits"))).optional();
		if (candidate.isEmpty()) return Optional.empty();
		AccessCandidate selected = candidate.get();

		if (selected.usageMode() == VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT) {
			Optional<UUID> baseConsumptionId = jdbcClient.sql("""
					SELECT id
					FROM visit_package_consumption
					WHERE organization_id=:organizationId
					  AND entitlement_id=:entitlementId
					  AND branch_id=:branchId
					  AND business_date=:businessDate
					  AND status='CONSUMED'
					ORDER BY occurred_at, id
					LIMIT 1
					""")
					.param("organizationId", organizationId)
					.param("entitlementId", selected.entitlementId())
					.param("branchId", branchId)
					.param("businessDate", businessDate)
					.query(UUID.class).optional();
			UUID includedAccessId = UUID.randomUUID();
			jdbcClient.sql("""
					INSERT INTO visit_package_included_access (
					    id, organization_id, entitlement_id, benefit_id, branch_id,
					    access_device_id, base_consumption_id, idempotency_key,
					    status, remaining_after, business_date, occurred_at, created_at
					) VALUES (
					    :id, :organizationId, :entitlementId, :benefitId, :branchId,
					    :accessDeviceId, :baseConsumptionId, :idempotencyKey,
					    :status, :remainingAfter, :businessDate, :occurredAt, :occurredAt
					)
					""")
					.param("id", includedAccessId).param("organizationId", organizationId)
					.param("entitlementId", selected.entitlementId())
					.param("benefitId", selected.benefitId()).param("branchId", branchId)
					.param("accessDeviceId", accessDeviceId)
					.param("baseConsumptionId", baseConsumptionId.orElse(null))
					.param("idempotencyKey", idempotencyKey).param("businessDate", businessDate)
					.param("status", baseConsumptionId.isPresent() ? "GRANTED" : "DENIED")
					.param("remainingAfter", selected.remainingAfter())
					.param("occurredAt", occurredAt).update();
			return usageByKey(organizationId, idempotencyKey);
		}

		int remainingAfter = jdbcClient.sql("""
				UPDATE visit_package_entitlement
				SET remaining_visits=remaining_visits-1,
				    status=CASE WHEN remaining_visits-1=0 THEN 'CONSUMED' ELSE status END,
				    updated_at=:occurredAt, version=version+1
				WHERE organization_id=:organizationId AND id=:entitlementId
				  AND status='ACTIVE' AND remaining_visits > 0
				RETURNING remaining_visits
				""")
				.param("organizationId", organizationId).param("entitlementId", selected.entitlementId())
				.param("occurredAt", occurredAt).query(Integer.class).single();
		UUID consumptionId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO visit_package_consumption (
				    id, organization_id, entitlement_id, benefit_id, branch_id, access_device_id,
				    idempotency_key, status, remaining_after, business_date, occurred_at, created_at
				) VALUES (
				    :id, :organizationId, :entitlementId, :benefitId, :branchId, :accessDeviceId,
				    :idempotencyKey, 'CONSUMED', :remainingAfter, :businessDate, :occurredAt, :occurredAt
				)
				""")
				.param("id", consumptionId).param("organizationId", organizationId)
				.param("entitlementId", selected.entitlementId())
				.param("benefitId", selected.benefitId()).param("branchId", branchId)
				.param("accessDeviceId", accessDeviceId).param("idempotencyKey", idempotencyKey)
				.param("remainingAfter", remainingAfter).param("businessDate", businessDate)
				.param("occurredAt", occurredAt).update();
		return usageByKey(organizationId, idempotencyKey);
	}

	@Override
	public boolean linkAccessLog(
			UUID organizationId, UUID usageId, VisitPackageBenefitUsageMode usageMode, UUID accessLogId
	) {
		String table = usageMode == VisitPackageBenefitUsageMode.CONSUMES_VISIT
				? "visit_package_consumption" : "visit_package_included_access";
		return jdbcClient.sql("UPDATE " + table + " SET access_log_id=:accessLogId "
				+ "WHERE organization_id=:organizationId AND id=:usageId "
				+ "AND (access_log_id IS NULL OR access_log_id=:accessLogId) "
				+ "AND EXISTS (SELECT 1 FROM access_log "
				+ "WHERE organization_id=:organizationId AND id=:accessLogId)")
				.param("organizationId", organizationId).param("usageId", usageId)
				.param("accessLogId", accessLogId).update() == 1;
	}

	private Optional<VisitPackageAccessResult> usageByKey(UUID organizationId, String idempotencyKey) {
		Optional<VisitPackageAccessResult> consumption = consumptionByKey(organizationId, idempotencyKey);
		return consumption.isPresent() ? consumption : includedAccessByKey(organizationId, idempotencyKey);
	}

	private Optional<VisitPackageAccessResult> consumptionByKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT consumption.id, consumption.id AS base_consumption_id, consumption.benefit_id,
				       consumption.entitlement_id, entitlement.client_id,
				       consumption.branch_id, consumption.access_device_id, product.name AS package_name,
				       consumption.remaining_after, consumption.access_log_id
				FROM visit_package_consumption consumption
				JOIN visit_package_entitlement entitlement
				  ON entitlement.organization_id=consumption.organization_id
				 AND entitlement.id=consumption.entitlement_id
				JOIN visit_package_plan plan
				  ON plan.organization_id=entitlement.organization_id
				 AND plan.id=entitlement.visit_package_plan_id
				JOIN catalog_product product
				  ON product.organization_id=plan.organization_id
				 AND product.id=plan.product_id
				WHERE consumption.organization_id=:organizationId
				  AND consumption.idempotency_key=:idempotencyKey
				""")
				.param("organizationId", organizationId).param("idempotencyKey", idempotencyKey)
				.query((rs, row) -> new VisitPackageAccessResult(
						rs.getObject("id", UUID.class), rs.getObject("base_consumption_id", UUID.class),
						rs.getObject("benefit_id", UUID.class), VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						rs.getObject("entitlement_id", UUID.class),
						rs.getObject("client_id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getObject("access_device_id", UUID.class), rs.getString("package_name"),
						rs.getInt("remaining_after"), true,
						rs.getObject("access_log_id", UUID.class)))
				.optional();
	}

	private Optional<VisitPackageAccessResult> includedAccessByKey(
			UUID organizationId, String idempotencyKey
	) {
		return jdbcClient.sql("""
				SELECT included.id, included.base_consumption_id, included.benefit_id, included.status,
				       included.entitlement_id, entitlement.client_id, included.branch_id,
				       included.access_device_id, product.name AS package_name,
				       included.remaining_after, included.access_log_id
				FROM visit_package_included_access included
				JOIN visit_package_entitlement entitlement
				  ON entitlement.organization_id=included.organization_id
				 AND entitlement.id=included.entitlement_id
				JOIN visit_package_plan plan
				  ON plan.organization_id=entitlement.organization_id
				 AND plan.id=entitlement.visit_package_plan_id
				JOIN catalog_product product
				  ON product.organization_id=plan.organization_id
				 AND product.id=plan.product_id
				WHERE included.organization_id=:organizationId
				  AND included.idempotency_key=:idempotencyKey
				""")
				.param("organizationId", organizationId).param("idempotencyKey", idempotencyKey)
				.query((rs, row) -> new VisitPackageAccessResult(
						rs.getObject("id", UUID.class), rs.getObject("base_consumption_id", UUID.class),
						rs.getObject("benefit_id", UUID.class), VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						rs.getObject("entitlement_id", UUID.class),
						rs.getObject("client_id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getObject("access_device_id", UUID.class), rs.getString("package_name"),
						rs.getInt("remaining_after"), "GRANTED".equals(rs.getString("status")),
						rs.getObject("access_log_id", UUID.class)))
				.optional();
	}

	private record AccessCandidate(
			UUID entitlementId, UUID benefitId, VisitPackageBenefitUsageMode usageMode,
			int remainingAfter
	) {}

	private Optional<VisitPackageEntitlementView> query(
			String predicate, UUID organizationId, UUID clientId, UUID saleId, String idempotencyKey,
			UUID filterEntitlementId
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
				WHERE
				""" + predicate + " AND entitlement.deleted_at IS NULL ORDER BY entitlement.created_at DESC LIMIT 1")
				.param("organizationId", organizationId);
		if (clientId != null) statement = statement.param("clientId", clientId);
		if (saleId != null) statement = statement.param("saleId", saleId);
		if (idempotencyKey != null) statement = statement.param("idempotencyKey", idempotencyKey);
		if (filterEntitlementId != null) statement = statement.param("entitlementId", filterEntitlementId);
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
			List<VisitPackageBenefitView> resolvedBenefits = benefits(organizationId, entitlementId);
			List<UUID> resolvedBenefitIds = resolvedBenefits.stream()
					.map(VisitPackageBenefitView::benefitId).toList();
			List<VisitPackageConsumptionView> resolvedConsumptions = consumptions(organizationId, entitlementId);
			return new VisitPackageEntitlementView(
					entitlementId, resolvedClientId, packagePlanId, resolvedSaleId,
					packageName, status, totalVisits, remainingVisits, validFrom, validUntil,
					pendingExpiresAt, resolvedBranchIds, resolvedBenefitIds, resolvedBenefits,
					resolvedConsumptions,
					createdAt, updatedAt);
		}).optional();
	}

	private List<UUID> branchIds(UUID organizationId, UUID entitlementId) {
		return jdbcClient.sql("""
				SELECT branch_id FROM visit_package_entitlement_branch
				WHERE organization_id=:organizationId AND entitlement_id=:entitlementId ORDER BY branch_id
				""").param("organizationId", organizationId).param("entitlementId", entitlementId)
				.query(UUID.class).list();
	}

	private List<VisitPackageBenefitView> benefits(UUID organizationId, UUID entitlementId) {
		return jdbcClient.sql("""
				SELECT entitlement_benefit.benefit_id, benefit.code, benefit.name,
				       entitlement_benefit.usage_mode,
				       COALESCE(array_agg(snapshot.branch_id ORDER BY snapshot.branch_id)
				           FILTER (WHERE snapshot.branch_id IS NOT NULL), ARRAY[]::uuid[]) AS branch_ids
				FROM visit_package_entitlement_benefit entitlement_benefit
				JOIN benefit benefit
				  ON benefit.organization_id=entitlement_benefit.organization_id
				 AND benefit.id=entitlement_benefit.benefit_id
				LEFT JOIN visit_package_entitlement_benefit_branch snapshot
				  ON snapshot.organization_id=entitlement_benefit.organization_id
				 AND snapshot.entitlement_id=entitlement_benefit.entitlement_id
				 AND snapshot.benefit_id=entitlement_benefit.benefit_id
				WHERE entitlement_benefit.organization_id=:organizationId
				  AND entitlement_benefit.entitlement_id=:entitlementId
				GROUP BY entitlement_benefit.benefit_id, benefit.code, benefit.name,
				         entitlement_benefit.usage_mode
				ORDER BY benefit.name, entitlement_benefit.benefit_id
				""").param("organizationId", organizationId).param("entitlementId", entitlementId)
				.query((rs, row) -> new VisitPackageBenefitView(
						rs.getObject("benefit_id", UUID.class), rs.getString("code"),
						rs.getString("name"), rs.getString("usage_mode"),
						List.of((UUID[]) rs.getArray("branch_ids").getArray())))
				.list();
	}

	private List<VisitPackageConsumptionView> consumptions(UUID organizationId, UUID entitlementId) {
		return jdbcClient.sql("""
				SELECT consumption.id, consumption.status, consumption.occurred_at,
				       consumption.branch_id, branch.name AS branch_name,
				       consumption.access_device_id, device.name AS device_name,
				       consumption.remaining_after
				FROM visit_package_consumption consumption
				JOIN branch branch
				  ON branch.organization_id=consumption.organization_id
				 AND branch.id=consumption.branch_id
				LEFT JOIN access_device device
				  ON device.organization_id=consumption.organization_id
				 AND device.id=consumption.access_device_id
				WHERE consumption.organization_id=:organizationId
				  AND consumption.entitlement_id=:entitlementId
				ORDER BY consumption.occurred_at DESC, consumption.id DESC
				LIMIT 20
				""")
				.param("organizationId", organizationId).param("entitlementId", entitlementId)
				.query((rs, row) -> new VisitPackageConsumptionView(
						rs.getObject("id", UUID.class), rs.getString("status"),
						rs.getObject("occurred_at", OffsetDateTime.class),
						rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
						rs.getObject("access_device_id", UUID.class), rs.getString("device_name"),
						rs.getInt("remaining_after")))
				.list();
	}
}
