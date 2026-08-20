package com.gymos.onboarding.infrastructure;

import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.BranchProfile;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.OrganizationProfile;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.PilotPlan;
import com.gymos.onboarding.application.result.OnboardingChecklistResult;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingRepository {
	private final JdbcClient jdbcClient;

	public OnboardingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void updateOrganization(UUID organizationId, UUID userId, OrganizationProfile organization, OffsetDateTime now) {
		if (organization == null) {
			return;
		}
		jdbcClient.sql("""
				UPDATE organization
				SET legal_name = COALESCE(:legalName, legal_name),
				    trade_name = COALESCE(:tradeName, trade_name),
				    tax_id = :taxId,
				    timezone = COALESCE(:timezone, timezone),
				    default_currency = COALESCE(:defaultCurrency, default_currency),
				    contact_email = :contactEmail,
				    contact_phone = :contactPhone,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("legalName", normalize(organization.legalName()))
				.param("tradeName", normalize(organization.tradeName()))
				.param("taxId", normalize(organization.taxId()))
				.param("timezone", normalize(organization.timezone()))
				.param("defaultCurrency", upper(organization.defaultCurrency()))
				.param("contactEmail", lower(organization.contactEmail()))
				.param("contactPhone", normalize(organization.contactPhone()))
				.param("updatedAt", now)
				.param("updatedBy", userId)
				.param("organizationId", organizationId)
				.update();
	}

	public void updateBranch(UUID organizationId, UUID branchId, UUID userId, BranchProfile branch, OffsetDateTime now) {
		if (branch == null) {
			return;
		}
		jdbcClient.sql("""
				UPDATE branch
				SET name = COALESCE(:name, name),
				    code = COALESCE(:code, code),
				    timezone = COALESCE(:timezone, timezone),
				    phone = :phone,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :branchId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("name", normalize(branch.name()))
				.param("code", upper(branch.code()))
				.param("timezone", normalize(branch.timezone()))
				.param("phone", normalize(branch.phone()))
				.param("updatedAt", now)
				.param("updatedBy", userId)
				.param("branchId", branchId)
				.param("organizationId", organizationId)
				.update();
	}

	public UUID createProduct(CreatePilotProductRecord record) {
		UUID productId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, description, product_type, price_cents,
				    currency, taxable, status, inventory_tracked,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :sku, :name, :description, :productType, :priceCents,
				    :currency, :taxable, 'ACTIVE', :inventoryTracked,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", productId)
				.param("organizationId", record.organizationId())
				.param("sku", record.sku())
				.param("name", record.name())
				.param("description", record.description())
				.param("productType", record.productType())
				.param("priceCents", record.priceCents())
				.param("currency", record.currency())
				.param("taxable", record.taxable())
				.param("inventoryTracked", "PHYSICAL".equals(record.productType()))
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
		return productId;
	}

	public void createPlan(UUID organizationId, UUID userId, UUID productId, PilotPlan plan, OffsetDateTime now) {
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				    id, organization_id, product_id, duration_days, grace_days, benefits_json,
				    status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("productId", productId)
				.param("durationDays", Math.max(1, plan.durationDays()))
				.param("graceDays", Math.max(0, plan.graceDays()))
				.param("benefitsJson", normalize(plan.benefitsJson()) == null ? "{}" : plan.benefitsJson())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", userId)
				.param("updatedBy", userId)
				.update();
	}

	public boolean ensureKiosk(UUID organizationId, UUID branchId, UUID userId, String terminalCode, String kioskName, OffsetDateTime now) {
		String deviceCode = normalize(terminalCode) == null ? "KIOSK-MAIN" : terminalCode.trim().toUpperCase(Locale.ROOT);
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM self_service_terminal
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND device_code = :deviceCode
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceCode", deviceCode)
				.query(Integer.class)
				.single();
		if (count > 0) {
			return false;
		}
		jdbcClient.sql("""
				INSERT INTO self_service_terminal (
				    id, organization_id, branch_id, name, device_code, status,
				    allowed_actions_json, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :name, :deviceCode, 'ACTIVE',
			    '{"membershipEnrollment":true,"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":true}'::jsonb,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("name", normalize(kioskName) == null ? "Kiosco principal" : kioskName.trim())
				.param("deviceCode", deviceCode)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", userId)
				.param("updatedBy", userId)
				.update();
		return true;
	}

	public OnboardingChecklistResult buildChecklist(UUID organizationId, UUID branchId) {
		ProfileCounts counts = jdbcClient.sql("""
				SELECT
				    EXISTS (
				        SELECT 1 FROM organization
				        WHERE id = :organizationId
				          AND deleted_at IS NULL
				          AND COALESCE(contact_email, '') <> ''
				          AND COALESCE(contact_phone, '') <> ''
				    ) AS organization_complete,
				    EXISTS (
				        SELECT 1 FROM branch
				        WHERE id = :branchId
				          AND organization_id = :organizationId
				          AND deleted_at IS NULL
				          AND COALESCE(phone, '') <> ''
				    ) AS branch_configured,
				    (SELECT COUNT(*) FROM membership_plan WHERE organization_id = :organizationId AND status = 'ACTIVE' AND deleted_at IS NULL) AS active_plan_count,
				    (SELECT COUNT(*) FROM catalog_product WHERE organization_id = :organizationId AND product_type <> 'MEMBERSHIP_PLAN' AND status = 'ACTIVE' AND deleted_at IS NULL) AS active_product_count,
				    (SELECT COUNT(*) FROM client WHERE organization_id = :organizationId AND deleted_at IS NULL) AS client_count,
				    (SELECT COUNT(*) FROM self_service_terminal WHERE organization_id = :organizationId AND status = 'ACTIVE' AND deleted_at IS NULL) AS active_kiosk_count
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((rs, rowNum) -> new ProfileCounts(
						rs.getBoolean("organization_complete"),
						rs.getBoolean("branch_configured"),
						rs.getLong("active_plan_count"),
						rs.getLong("active_product_count"),
						rs.getLong("client_count"),
						rs.getLong("active_kiosk_count")
				))
				.single();
		boolean hasPlans = counts.activePlanCount() > 0;
		boolean hasProducts = counts.activeProductCount() > 0;
		boolean hasClients = counts.clientCount() > 0;
		boolean hasKiosk = counts.activeKioskCount() > 0;
		boolean ready = counts.organizationComplete() && counts.branchConfigured() && hasPlans && hasProducts && hasKiosk;
		return new OnboardingChecklistResult(
				counts.organizationComplete(),
				counts.branchConfigured(),
				hasPlans,
				hasProducts,
				hasKiosk,
				hasClients,
				ready,
				counts.activePlanCount(),
				counts.activeProductCount(),
				counts.clientCount(),
				counts.activeKioskCount()
		);
	}

	public Optional<UUID> productIdBySku(UUID organizationId, String sku) {
		return jdbcClient.sql("""
				SELECT id
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND sku = :sku
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("sku", sku)
				.query(UUID.class)
				.optional();
	}

	public boolean planExistsForProduct(UUID organizationId, UUID productId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership_plan
				WHERE organization_id = :organizationId
				  AND product_id = :productId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	private String upper(String value) {
		String normalized = normalize(value);
		return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
	}

	private String lower(String value) {
		String normalized = normalize(value);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record CreatePilotProductRecord(
			UUID organizationId,
			UUID userId,
			String sku,
			String name,
			String description,
			String productType,
			long priceCents,
			String currency,
			boolean taxable,
			OffsetDateTime now
	) {
	}

	private record ProfileCounts(
			boolean organizationComplete,
			boolean branchConfigured,
			long activePlanCount,
			long activeProductCount,
			long clientCount,
			long activeKioskCount
	) {
	}
}
