package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackagePlanOperations;
import com.gymos.catalog.api.VisitPackagePlanOperations.VisitPackagePlanSnapshot;
import com.gymos.catalog.api.VisitPackagePlanOperations.BenefitCoverage;
import com.gymos.catalog.api.VisitPackageBenefitUsage;
import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.catalog.application.command.CreateVisitPackagePlanCommand;
import com.gymos.catalog.application.command.UpdateVisitPackagePlanCommand;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.VisitPackagePlanPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.VisitPackagePlanView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitPackagePlanService implements VisitPackagePlanOperations {
	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort productCommands;
	private final VisitPackagePlanPort packages;
	private final AuditService auditService;

	public VisitPackagePlanService(
			CatalogProductQueryPort products,
			CatalogProductCommandPort productCommands,
			VisitPackagePlanPort packages,
			AuditService auditService
	) {
		this.products = products;
		this.productCommands = productCommands;
		this.packages = packages;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<VisitPackagePlanView> plans(GymOsPrincipal principal) {
		CatalogAccessGuard.requireBranch(principal);
		return packages.list(principal.organizationId(), principal.branchId());
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<VisitPackagePlanSnapshot> activePlan(
			UUID organizationId, UUID branchId, UUID planId
	) {
		if (organizationId == null || branchId == null || planId == null) return java.util.Optional.empty();
		return packages.find(organizationId, planId)
				.filter(plan -> "ACTIVE".equals(plan.status()))
				.filter(plan -> "ALL_BRANCHES".equals(plan.branchScope()) || plan.branchIds().contains(branchId))
				.map(plan -> snapshot(organizationId, plan));
	}

	@Override
	@Transactional(readOnly = true)
	public List<VisitPackagePlanSnapshot> selfServicePlans(UUID organizationId, UUID branchId) {
		if (organizationId == null || branchId == null) return List.of();
		return packages.list(organizationId, branchId).stream()
				.filter(plan -> "ACTIVE".equals(plan.status()))
				.filter(VisitPackagePlanView::selfServiceAcquisitionEnabled)
				.map(plan -> snapshot(organizationId, plan))
				.toList();
	}

	@Transactional
	public VisitPackagePlanView create(
			GymOsPrincipal principal, CreateVisitPackagePlanCommand command, String correlationId
	) {
		CatalogAccessGuard.requireBranch(principal);
		validatePrice(command.priceCents());
		ValidatedDefinition definition = validate(
				principal.organizationId(), principal.branchId(), command.totalVisits(), command.validityDays(), command.branchScope(),
				command.branchIds(), command.benefits(), command.registrationFeePolicy(), command.status());
		String sku = required(command.sku(), "VISIT_PACKAGE_SKU_REQUIRED").toUpperCase();
		if (products.findBySku(principal.organizationId(), sku).isPresent()) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ese código ya pertenece a otro artículo o plan.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		UUID productId = UUID.randomUUID();
		UUID packageId = UUID.randomUUID();
		productCommands.create(new ProductMutation(
				principal.organizationId(), productId, sku, required(command.name(), "VISIT_PACKAGE_NAME_REQUIRED"),
				blankToNull(command.description()), "SERVICE", "VISIT_PASS", command.priceCents(),
				normalizeCurrency(command.currency()), true, definition.status(), null, null, null, null,
				false, 0, null, null, null, null, null, null, null, now, principal.userId()));
		for (UUID branchId : definition.effectiveBranchIds()) {
			productCommands.attachBranch(principal.organizationId(), branchId, productId, principal.userId(), now);
		}
		packages.create(
				principal.organizationId(), packageId, productId, command.totalVisits(), command.validityDays(),
				definition.branchScope(), command.selfServiceAcquisitionEnabled(), definition.registrationFeePolicy(),
				definition.status(), principal.userId(), now);
		packages.replaceBranches(
				principal.organizationId(), packageId,
				"SELECTED_BRANCHES".equals(definition.branchScope()) ? definition.effectiveBranchIds() : List.of(),
				principal.userId());
		packages.replaceBenefits(principal.organizationId(), packageId, definition.benefits(), principal.userId());
		auditService.record(principal, "VISIT_PACKAGE_PLAN_CREATED", "VISIT_PACKAGE_PLAN", packageId,
				"BACKOFFICE", null, correlationId);
		return packages.find(principal.organizationId(), packageId).orElseThrow();
	}

	@Transactional
	public VisitPackagePlanView update(
			GymOsPrincipal principal, UUID planId, UpdateVisitPackagePlanCommand command, String correlationId
	) {
		CatalogAccessGuard.requireBranch(principal);
		validatePrice(command.priceCents());
		VisitPackagePlanView current = packages.find(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("VISIT_PACKAGE_PLAN_NOT_FOUND", "El paquete de visitas no está disponible."));
		ValidatedDefinition definition = validate(
				principal.organizationId(), principal.branchId(), command.totalVisits(), command.validityDays(), command.branchScope(),
				command.branchIds(), command.benefits(), command.registrationFeePolicy(), command.status());
		ProductSummaryView product = products.findBySku(principal.organizationId(), current.sku())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto del paquete no está disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		int productUpdated = productCommands.update(new ProductMutation(
				principal.organizationId(), current.productId(), current.sku(),
				required(command.name(), "VISIT_PACKAGE_NAME_REQUIRED"), blankToNull(command.description()),
				"SERVICE", "VISIT_PASS", command.priceCents(), normalizeCurrency(command.currency()), true,
				definition.status(), product.barcode(), product.brand(), product.presentation(), product.unitOfMeasure(),
				false, product.displayOrder(), product.imageObjectKey(), product.supplierName(), product.referenceCostCents(),
				product.promotionalPriceCents(), product.promotionStartsAt(), product.promotionEndsAt(),
				product.version(), now, principal.userId(), product.familyId(), product.variantName()));
		if (productUpdated != 1) {
			throw new BusinessException("PRODUCT_VERSION_CONFLICT", "El paquete fue modificado por otra operación.");
		}
		if (!packages.update(
				principal.organizationId(), planId, command.totalVisits(), command.validityDays(),
				definition.branchScope(), command.selfServiceAcquisitionEnabled(), definition.registrationFeePolicy(),
				definition.status(), command.expectedVersion(), principal.userId(), now)) {
			throw new BusinessException("VISIT_PACKAGE_PLAN_VERSION_CONFLICT", "El paquete fue modificado por otra operación.");
		}
		for (UUID branchId : definition.effectiveBranchIds()) {
			productCommands.attachBranch(principal.organizationId(), branchId, current.productId(), principal.userId(), now);
		}
		packages.replaceBranches(
				principal.organizationId(), planId,
				"SELECTED_BRANCHES".equals(definition.branchScope()) ? definition.effectiveBranchIds() : List.of(),
				principal.userId());
		packages.replaceBenefits(principal.organizationId(), planId, definition.benefits(), principal.userId());
		auditService.record(principal, "VISIT_PACKAGE_PLAN_UPDATED", "VISIT_PACKAGE_PLAN", planId,
				"BACKOFFICE", null, correlationId);
		return packages.find(principal.organizationId(), planId).orElseThrow();
	}

	private ValidatedDefinition validate(
			UUID organizationId, UUID activeBranchId, int totalVisits, int validityDays, String branchScope,
			List<UUID> branchIds, List<VisitPackageBenefitUsage> benefits,
			String registrationFeePolicy, String status
	) {
		if (totalVisits < 1 || totalVisits > 10_000) {
			throw new BusinessException("VISIT_PACKAGE_TOTAL_INVALID", "Las visitas deben estar entre 1 y 10000.");
		}
		if (validityDays < 1 || validityDays > 3_650) {
			throw new BusinessException("VISIT_PACKAGE_VALIDITY_INVALID", "La vigencia debe estar entre 1 y 3650 días.");
		}
		String normalizedScope = required(branchScope, "VISIT_PACKAGE_BRANCH_SCOPE_REQUIRED").toUpperCase();
		if (!"ALL_BRANCHES".equals(normalizedScope) && !"SELECTED_BRANCHES".equals(normalizedScope)) {
			throw new BusinessException("VISIT_PACKAGE_BRANCH_SCOPE_INVALID", "Selecciona un alcance de sucursales válido.");
		}
		List<UUID> effectiveBranches = "ALL_BRANCHES".equals(normalizedScope)
				? packages.activeBranchIds(organizationId)
				: branchIds.stream().distinct().toList();
		if (effectiveBranches.isEmpty() || !packages.branchesExist(organizationId, effectiveBranches)) {
			throw new BusinessException("VISIT_PACKAGE_BRANCHES_INVALID", "Selecciona al menos una sucursal activa del gimnasio.");
		}
		if (!effectiveBranches.contains(activeBranchId)) {
			throw new BusinessException(
					"VISIT_PACKAGE_ACTIVE_BRANCH_REQUIRED",
					"El paquete debe incluir la sucursal en la que estás operando.");
		}
		List<VisitPackageBenefitUsage> normalizedBenefits = normalizeBenefits(benefits);
		List<UUID> distinctBenefitIds = normalizedBenefits.stream()
				.map(VisitPackageBenefitUsage::benefitId).toList();
		if (distinctBenefitIds.isEmpty() || !packages.accessBenefitsExist(organizationId, distinctBenefitIds)) {
			throw new BusinessException("VISIT_PACKAGE_BENEFITS_INVALID", "Selecciona al menos un acceso activo del gimnasio.");
		}
		if (normalizedBenefits.stream().noneMatch(
				benefit -> benefit.usageMode() == VisitPackageBenefitUsageMode.CONSUMES_VISIT)) {
			throw new BusinessException(
					"VISIT_PACKAGE_CONSUMING_BENEFIT_REQUIRED",
					"Selecciona al menos un acceso que descuente una visita.");
		}
		List<VisitPackagePlanPort.BenefitCoverage> benefitCoverages =
				packages.benefitCoverages(organizationId, normalizedBenefits, effectiveBranches);
		if (benefitCoverages.stream().anyMatch(coverage -> coverage.branchIds().isEmpty())) {
			throw new BusinessException(
					"VISIT_PACKAGE_BENEFIT_OUTSIDE_SCOPE",
					"Uno de los accesos elegidos no se reconoce en ninguna sucursal de este paquete.");
		}
		var branchesWithBaseVisit = new HashSet<UUID>();
		benefitCoverages.stream()
				.filter(coverage -> coverage.usageMode() == VisitPackageBenefitUsageMode.CONSUMES_VISIT)
				.forEach(coverage -> branchesWithBaseVisit.addAll(coverage.branchIds()));
		if (!branchesWithBaseVisit.containsAll(effectiveBranches)) {
			throw new BusinessException(
					"VISIT_PACKAGE_BRANCH_WITHOUT_ACCESS",
					"Cada sucursal del paquete debe tener al menos un acceso que descuente una visita.");
		}
		String feePolicy = required(registrationFeePolicy, "REGISTRATION_FEE_POLICY_INVALID").toUpperCase();
		if (!"DEFAULT".equals(feePolicy) && !"WAIVED".equals(feePolicy)) {
			throw new BusinessException("REGISTRATION_FEE_POLICY_INVALID", "Selecciona una política de inscripción válida.");
		}
		String normalizedStatus = required(status, "VISIT_PACKAGE_STATUS_INVALID").toUpperCase();
		if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
			throw new BusinessException("VISIT_PACKAGE_STATUS_INVALID", "Selecciona un estado válido.");
		}
		return new ValidatedDefinition(normalizedScope, effectiveBranches, normalizedBenefits, feePolicy, normalizedStatus);
	}

	private List<VisitPackageBenefitUsage> normalizeBenefits(List<VisitPackageBenefitUsage> benefits) {
		if (benefits == null || benefits.isEmpty()) return List.of();
		var byBenefit = new LinkedHashMap<UUID, VisitPackageBenefitUsage>();
		for (VisitPackageBenefitUsage benefit : benefits) {
			if (benefit == null || benefit.benefitId() == null || benefit.usageMode() == null) {
				throw new BusinessException(
						"VISIT_PACKAGE_BENEFITS_INVALID", "Completa el acceso y cómo utiliza el saldo.");
			}
			if (byBenefit.putIfAbsent(benefit.benefitId(), benefit) != null) {
				throw new BusinessException(
						"VISIT_PACKAGE_BENEFIT_DUPLICATED", "No repitas el mismo acceso dentro del paquete.");
			}
		}
		return List.copyOf(byBenefit.values());
	}

	private String normalizeCurrency(String value) {
		String currency = required(value, "VISIT_PACKAGE_CURRENCY_REQUIRED").toUpperCase();
		if (!currency.matches("[A-Z]{3}")) {
			throw new BusinessException("VISIT_PACKAGE_CURRENCY_INVALID", "La moneda debe tener tres letras.");
		}
		return currency;
	}

	private void validatePrice(long priceCents) {
		if (priceCents < 0) {
			throw new BusinessException("VISIT_PACKAGE_PRICE_INVALID", "El precio del paquete no puede ser negativo.");
		}
	}

	private VisitPackagePlanSnapshot snapshot(UUID organizationId, VisitPackagePlanView plan) {
		List<UUID> effectiveBranches = "ALL_BRANCHES".equals(plan.branchScope())
				? packages.activeBranchIds(organizationId) : plan.branchIds();
		List<BenefitCoverage> coverages = packages.benefitCoverages(
				organizationId, plan.benefits(), effectiveBranches).stream()
				.map(coverage -> new BenefitCoverage(
						coverage.benefitId(), coverage.usageMode(), coverage.branchIds()))
				.toList();
		return new VisitPackagePlanSnapshot(
				plan.id(), plan.productId(), plan.sku(), plan.name(), plan.priceCents(), plan.currency(),
				plan.totalVisits(), plan.validityDays(), plan.branchScope(),
				effectiveBranches, plan.benefits(), coverages,
				plan.selfServiceAcquisitionEnabled(), plan.registrationFeePolicy());
	}

	private String required(String value, String code) {
		if (value == null || value.isBlank()) throw new BusinessException(code, "Completa los datos requeridos del paquete.");
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record ValidatedDefinition(
			String branchScope, List<UUID> effectiveBranchIds, List<VisitPackageBenefitUsage> benefits,
			String registrationFeePolicy, String status
	) {}
}
