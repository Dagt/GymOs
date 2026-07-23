package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.command.UpdateMembershipPlanCommand;
import com.gymos.catalog.infrastructure.CatalogCommandRepository;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.PlanRecord;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.PlanUpdateRecord;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.ProductUpdateRecord;
import com.gymos.catalog.infrastructure.CatalogRepository;
import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogMembershipPlanService {
	private final CatalogRepository catalogRepository;
	private final CatalogCommandRepository commandRepository;
	private final CatalogProductService productService;
	private final CatalogBenefitsSerializer benefitsSerializer;
	private final AuditService auditService;

	CatalogMembershipPlanService(CatalogRepository catalogRepository, CatalogCommandRepository commandRepository,
			CatalogProductService productService, CatalogBenefitsSerializer benefitsSerializer, AuditService auditService) {
		this.catalogRepository = catalogRepository;
		this.commandRepository = commandRepository;
		this.productService = productService;
		this.benefitsSerializer = benefitsSerializer;
		this.auditService = auditService;
	}

	List<PlanSummary> plans(GymOsPrincipal principal) {
		CatalogAccessGuard.requirePrincipal(principal);
		return catalogRepository.plans(principal.organizationId());
	}

	PlanSummary createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummary product = catalogRepository.product(principal.organizationId(), request.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
		if (!"MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_NOT_MEMBERSHIP_PLAN", "El producto debe ser de tipo MEMBERSHIP_PLAN.");
		}
		UUID planId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.createPlan(new PlanRecord(
				planId,
				principal.organizationId(),
				request.productId(),
				request.durationDays(),
				request.graceDays(),
				benefitsSerializer.normalizeJson(request.benefitsJson()),
				request.multiBranchAccess(),
				request.status().trim().toUpperCase(),
				now,
				principal.userId()
		));
		auditService.record(principal, "PLAN_CREATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return catalogRepository.plan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
	}

	PlanSummary createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String sku = request.sku().trim().toUpperCase();
		if (catalogRepository.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto o plan con ese codigo.");
		}

		ProductSummary product = productService.createProduct(
				principal,
				new ProductCommand(
						sku,
						request.name(),
						request.description(),
						"MEMBERSHIP_PLAN",
						request.priceCents(),
						request.currency(),
						true,
						request.status()
				),
				correlationId
		);
		return createPlan(
				principal,
				new CreatePlanCommand(
						product.id(),
						request.durationDays(),
						request.graceDays(),
						benefitsSerializer.textToJson(request.benefits()),
						request.multiBranchAccess(),
						request.status()
				),
				correlationId
		);
	}

	PlanSummary updateMembershipPlan(GymOsPrincipal principal, UUID planId, UpdateMembershipPlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		PlanSummary existing = catalogRepository.plan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.updateProduct(new ProductUpdateRecord(
				principal.organizationId(),
				existing.productId(),
				request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()),
				request.priceCents(),
				request.currency().trim().toUpperCase(),
				request.status().trim().toUpperCase(),
				now,
				principal.userId()
		));
		commandRepository.updatePlan(new PlanUpdateRecord(
				principal.organizationId(),
				planId,
				request.durationDays(),
				request.graceDays(),
				benefitsSerializer.textToJson(request.benefits()),
				request.multiBranchAccess(),
				request.status().trim().toUpperCase(),
				now,
				principal.userId()
		));
		auditService.record(principal, "PLAN_UPDATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return catalogRepository.plan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
	}
}
