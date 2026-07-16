package com.gymos.onboarding.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.PilotPlan;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.PilotProduct;
import com.gymos.onboarding.application.result.OnboardingChecklistResult;
import com.gymos.onboarding.application.result.PilotSetupResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
	private final JdbcClient jdbcClient;
	private final AuditService auditService;

	public OnboardingService(JdbcClient jdbcClient, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public OnboardingChecklistResult checklist(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return buildChecklist(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public PilotSetupResult applyPilotSetup(
			GymOsPrincipal principal,
			ApplyPilotSetupCommand command,
			String correlationId
	) {
		requirePrincipal(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal antes de configurar el piloto.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		updateOrganization(principal, command.organization(), now);
		updateBranch(principal, command.branch(), now);
		int productsCreated = 0;
		int plansCreated = 0;
		for (PilotPlan plan : safePlans(command.plans())) {
			Optional<UUID> existingProductId = productIdBySku(principal.organizationId(), plan.sku());
			UUID productId;
			if (existingProductId.isPresent()) {
				productId = existingProductId.get();
			} else {
				productId = createProduct(
							principal,
							plan.sku(),
							plan.name(),
							plan.description(),
							"MEMBERSHIP_PLAN",
							plan.priceCents(),
							plan.currency(),
							true,
							now
					);
				productsCreated++;
			}
			if (!planExistsForProduct(principal.organizationId(), productId)) {
				createPlan(principal, productId, plan, now);
				plansCreated++;
			}
		}
		for (PilotProduct product : safeProducts(command.products())) {
			if (productIdBySku(principal.organizationId(), product.sku()).isEmpty()) {
				createProduct(
						principal,
						product.sku(),
						product.name(),
						product.description(),
						product.productType(),
						product.priceCents(),
						product.currency(),
						product.taxable(),
						now
				);
				productsCreated++;
			}
		}
		boolean kioskCreated = ensureKiosk(principal, command.kioskTerminalCode(), command.kioskName(), now);
		auditService.record(principal, "PILOT_SETUP_APPLIED", "ORGANIZATION", principal.organizationId(), "BACKOFFICE", null, correlationId);
		return new PilotSetupResult(buildChecklist(principal.organizationId(), principal.branchId()), productsCreated, plansCreated, kioskCreated);
	}

	private void updateOrganization(
			GymOsPrincipal principal,
			ApplyPilotSetupCommand.OrganizationProfile organization,
			OffsetDateTime now
	) {
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
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.update();
	}

	private void updateBranch(
			GymOsPrincipal principal,
			ApplyPilotSetupCommand.BranchProfile branch,
			OffsetDateTime now
	) {
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
				.param("updatedBy", principal.userId())
				.param("branchId", principal.branchId())
				.param("organizationId", principal.organizationId())
				.update();
	}

	private UUID createProduct(
			GymOsPrincipal principal,
			String sku,
			String name,
			String description,
			String productType,
			long priceCents,
			String currency,
			boolean taxable,
			OffsetDateTime now
	) {
		UUID productId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, description, product_type, price_cents,
				    currency, taxable, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :sku, :name, :description, :productType, :priceCents,
				    :currency, :taxable, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", productId)
				.param("organizationId", principal.organizationId())
				.param("sku", requiredUpper(sku, "PRODUCT_SKU_REQUIRED"))
				.param("name", required(name, "PRODUCT_NAME_REQUIRED"))
				.param("description", normalize(description))
				.param("productType", requiredUpper(productType, "PRODUCT_TYPE_REQUIRED"))
				.param("priceCents", priceCents)
				.param("currency", requiredUpper(currency, "PRODUCT_CURRENCY_REQUIRED"))
				.param("taxable", taxable)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		return productId;
	}

	private void createPlan(GymOsPrincipal principal, UUID productId, PilotPlan plan, OffsetDateTime now) {
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
				.param("organizationId", principal.organizationId())
				.param("productId", productId)
				.param("durationDays", Math.max(1, plan.durationDays()))
				.param("graceDays", Math.max(0, plan.graceDays()))
				.param("benefitsJson", normalize(plan.benefitsJson()) == null ? "{}" : plan.benefitsJson())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
	}

	private boolean ensureKiosk(GymOsPrincipal principal, String terminalCode, String kioskName, OffsetDateTime now) {
		String deviceCode = normalize(terminalCode) == null ? "KIOSK-MAIN" : terminalCode.trim().toUpperCase(Locale.ROOT);
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM self_service_terminal
				WHERE organization_id = :organizationId
				  AND device_code = :deviceCode
				  AND deleted_at IS NULL
				""")
				.param("organizationId", principal.organizationId())
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
				    '{"renewal":true,"planChange":true,"pendingPayments":true}'::jsonb,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("name", normalize(kioskName) == null ? "Kiosco principal" : kioskName.trim())
				.param("deviceCode", deviceCode)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		return true;
	}

	private OnboardingChecklistResult buildChecklist(UUID organizationId, UUID branchId) {
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

	private Optional<UUID> productIdBySku(UUID organizationId, String sku) {
		String normalizedSku = requiredUpper(sku, "PRODUCT_SKU_REQUIRED");
		return jdbcClient.sql("""
				SELECT id
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND sku = :sku
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("sku", normalizedSku)
				.query(UUID.class)
				.optional();
	}

	private boolean planExistsForProduct(UUID organizationId, UUID productId) {
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

	private List<PilotPlan> safePlans(List<PilotPlan> plans) {
		return plans == null ? List.of() : plans;
	}

	private List<PilotProduct> safeProducts(List<PilotProduct> products) {
		return products == null ? List.of() : products;
	}

	private String required(String value, String code) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new BusinessException(code, "Falta un dato obligatorio del setup.");
		}
		return normalized;
	}

	private String requiredUpper(String value, String code) {
		return required(value, code).toUpperCase(Locale.ROOT);
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

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
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
