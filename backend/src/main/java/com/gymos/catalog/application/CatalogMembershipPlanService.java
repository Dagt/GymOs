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
		CatalogAccessGuard.requireBranch(principal);
		return plans.listPlans(principal.organizationId(), principal.branchId());
	}

	PlanView createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		ProductSummaryView product = products.findProduct(principal.organizationId(), principal.branchId(), request.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible en esta sucursal."));
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
		return plans.findPlan(principal.organizationId(), principal.branchId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible en esta sucursal."));
	}

	PlanView createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		String sku = request.sku().trim().toUpperCase();
		ProductSummaryView existingProduct = products.findBySku(principal.organizationId(), sku).orElse(null);
		if (existingProduct != null) {
			if (!"MEMBERSHIP_PLAN".equals(existingProduct.productType())) {
				throw new BusinessException(
						"PRODUCT_SKU_ALREADY_EXISTS",
						"Ese codigo ya pertenece a otro articulo del gimnasio. Usa un codigo distinto."
				);
			}
			PlanView existingPlan = plans.findPlanByProduct(
					principal.organizationId(), existingProduct.id()
			).orElseThrow(() -> new BusinessException(
					"PLAN_CONFIGURATION_INCOMPLETE",
					"El codigo pertenece a un plan incompleto. Contacta a soporte antes de continuar."
			));
			OffsetDateTime now = OffsetDateTime.now();
			productCommands.attachBranch(
					principal.organizationId(), principal.branchId(), existingProduct.id(), principal.userId(), now
			);
			auditService.record(
					principal, "PLAN_ATTACHED_TO_BRANCH", "MEMBERSHIP_PLAN", existingPlan.id(),
					"BACKOFFICE", "SKU " + sku, correlationId
			);
			return plans.findPlan(principal.organizationId(), principal.branchId(), existingPlan.id())
					.orElseThrow(() -> new BusinessException(
							"PLAN_NOT_FOUND", "No fue posible habilitar el plan en esta sucursal."
					));
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
		productCommands.attachBranch(principal.organizationId(), principal.branchId(), productId, principal.userId(), now);
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
		CatalogAccessGuard.requireBranch(principal);
		PlanView existing = plans.findPlan(principal.organizationId(), principal.branchId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible en esta sucursal."));
		ProductSummaryView product = products.findProduct(principal.organizationId(), principal.branchId(), existing.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto del plan no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		int updatedProducts = productCommands.update(new ProductMutation(
				principal.organizationId(), existing.productId(), product.sku(), request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()), "MEMBERSHIP_PLAN", "MEMBERSHIP",
				request.priceCents(), request.currency().trim().toUpperCase(), true,
				request.status().trim().toUpperCase(), product.barcode(), product.brand(), product.presentation(),
				product.unitOfMeasure(), false, product.displayOrder(), product.imageObjectKey(),
				product.supplierName(), product.referenceCostCents(), product.promotionalPriceCents(),
				product.promotionStartsAt(), product.promotionEndsAt(), product.version(), now, principal.userId()
		));
		if (updatedProducts != 1) {
			throw new BusinessException(
					"PRODUCT_VERSION_CONFLICT",
					"El plan fue modificado por otra operacion. Recarga la pagina e intenta nuevamente."
			);
		}
		planCommands.update(new PlanMutation(
				principal.organizationId(), planId, existing.productId(), request.durationDays(), request.graceDays(),
				benefitsSerializer.textToJson(request.benefits()), request.multiBranchAccess(),
				request.status().trim().toUpperCase(), now, principal.userId()
		));
		auditService.record(principal, "PLAN_UPDATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return plans.findPlan(principal.organizationId(), principal.branchId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible en esta sucursal."));
	}
}
