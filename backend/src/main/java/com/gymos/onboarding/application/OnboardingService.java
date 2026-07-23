package com.gymos.onboarding.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.PilotPlan;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand.PilotProduct;
import com.gymos.onboarding.application.result.OnboardingChecklistResult;
import com.gymos.onboarding.application.result.PilotSetupResult;
import com.gymos.onboarding.infrastructure.OnboardingRepository;
import com.gymos.onboarding.infrastructure.OnboardingRepository.CreatePilotProductRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
	private final OnboardingRepository onboardingRepository;
	private final AuditService auditService;

	public OnboardingService(OnboardingRepository onboardingRepository, AuditService auditService) {
		this.onboardingRepository = onboardingRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public OnboardingChecklistResult checklist(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return onboardingRepository.buildChecklist(principal.organizationId(), principal.branchId());
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
		onboardingRepository.updateOrganization(principal.organizationId(), principal.userId(), command.organization(), now);
		onboardingRepository.updateBranch(principal.organizationId(), principal.branchId(), principal.userId(), command.branch(), now);
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
		boolean kioskCreated = onboardingRepository.ensureKiosk(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				command.kioskTerminalCode(),
				command.kioskName(),
				now
		);
		auditService.record(principal, "PILOT_SETUP_APPLIED", "ORGANIZATION", principal.organizationId(), "BACKOFFICE", null, correlationId);
		return new PilotSetupResult(
				onboardingRepository.buildChecklist(principal.organizationId(), principal.branchId()),
				productsCreated,
				plansCreated,
				kioskCreated
		);
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
		return onboardingRepository.createProduct(new CreatePilotProductRecord(
				principal.organizationId(),
				principal.userId(),
				requiredUpper(sku, "PRODUCT_SKU_REQUIRED"),
				required(name, "PRODUCT_NAME_REQUIRED"),
				normalize(description),
				requiredUpper(productType, "PRODUCT_TYPE_REQUIRED"),
				priceCents,
				requiredUpper(currency, "PRODUCT_CURRENCY_REQUIRED"),
				taxable,
				now
		));
	}

	private void createPlan(GymOsPrincipal principal, UUID productId, PilotPlan plan, OffsetDateTime now) {
		onboardingRepository.createPlan(principal.organizationId(), principal.userId(), productId, plan, now);
	}

	private Optional<UUID> productIdBySku(UUID organizationId, String sku) {
		String normalizedSku = requiredUpper(sku, "PRODUCT_SKU_REQUIRED");
		return onboardingRepository.productIdBySku(organizationId, normalizedSku);
	}

	private boolean planExistsForProduct(UUID organizationId, UUID productId) {
		return onboardingRepository.planExistsForProduct(organizationId, productId);
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

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}
}
