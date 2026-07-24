package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.UpdateMembershipPlanCommand;
import com.gymos.catalog.application.port.CatalogPlanCommandPort;
import com.gymos.catalog.application.port.CatalogPlanCommandPort.PlanMutation;
import com.gymos.catalog.application.port.CatalogPlanQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogMembershipPlanService {
	private final CatalogProductQueryPort products;
	private final CatalogPlanQueryPort plans;
	private final CatalogProductCommandPort productCommands;
	private final CatalogPlanCommandPort planCommands;
	private final CatalogBenefitsSerializer benefitsSerializer;
	private final AuditService auditService;

	CatalogMembershipPlanService(CatalogProductQueryPort products, CatalogPlanQueryPort plans,
			CatalogProductCommandPort productCommands, CatalogPlanCommandPort planCommands,
			CatalogBenefitsSerializer benefitsSerializer, AuditService auditService) {
		this.products = products;
		this.plans = plans;
		this.productCommands = productCommands;
		this.planCommands = planCommands;
		this.benefitsSerializer = benefitsSerializer;
		this.auditService = auditService;
	}

	List<PlanView> plans(GymOsPrincipal principal) {
		CatalogAccessGuard.requirePrincipal(principal);
		return plans.listPlans(principal.organizationId());
	}

	PlanView createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = products.findProduct(principal.organizationId(), request.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
		if (!"MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_NOT_MEMBERSHIP_PLAN", "El producto debe ser de tipo MEMBERSHIP_PLAN.");
		}
		UUID planId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		planCommands.create(new PlanMutation(
				principal.organizationId(), planId, request.productId(), request.durationDays(), request.graceDays(),
				benefitsSerializer.normalizeJson(request.benefitsJson()), request.multiBranchAccess(),
				request.status().trim().toUpperCase(), now, principal.userId()
		));
		auditService.record(principal, "PLAN_CREATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return plans.findPlan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
	}

	PlanView createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String sku = request.sku().trim().toUpperCase();
		if (products.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto o plan con ese codigo.");
		}

		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		productCommands.create(new ProductMutation(
				principal.organizationId(), productId, sku, request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()), "MEMBERSHIP_PLAN", "MEMBERSHIP",
				request.priceCents(), request.currency().trim().toUpperCase(), true,
				request.status().trim().toUpperCase(), null, null, null, null,
				false, 0, null, null, null, null, null, null, now, principal.userId()
		));
		auditService.record(principal, "PRODUCT_CREATED", "PRODUCT", productId, "BACKOFFICE", null, correlationId);

		return createPlan(
				principal,
				new CreatePlanCommand(productId, request.durationDays(), request.graceDays(),
						benefitsSerializer.textToJson(request.benefits()), request.multiBranchAccess(), request.status()),
				correlationId
		);
	}

	PlanView updateMembershipPlan(GymOsPrincipal principal, UUID planId,
			UpdateMembershipPlanCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		PlanView existing = plans.findPlan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
		ProductSummaryView product = products.findProduct(principal.organizationId(), existing.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto del plan no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		productCommands.update(new ProductMutation(
				principal.organizationId(), existing.productId(), product.sku(), request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()), "MEMBERSHIP_PLAN", "MEMBERSHIP",
				request.priceCents(), request.currency().trim().toUpperCase(), true,
				request.status().trim().toUpperCase(), product.barcode(), product.brand(), product.presentation(),
				product.unitOfMeasure(), false, product.displayOrder(), product.imageObjectKey(),
				product.supplierName(), product.referenceCostCents(), product.promotionalPriceCents(),
				product.promotionStartsAt(), product.promotionEndsAt(), now, principal.userId()
		));
		planCommands.update(new PlanMutation(
				principal.organizationId(), planId, existing.productId(), request.durationDays(), request.graceDays(),
				benefitsSerializer.textToJson(request.benefits()), request.multiBranchAccess(),
				request.status().trim().toUpperCase(), now, principal.userId()
		));
		auditService.record(principal, "PLAN_UPDATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return plans.findPlan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
	}
}
