package com.gymos.catalog.application;

import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.command.UpdateMembershipPlanCommand;
import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final CatalogProductService productService;
	private final CatalogMembershipPlanService membershipPlanService;

	public CatalogService(CatalogProductService productService, CatalogMembershipPlanService membershipPlanService) {
		this.productService = productService;
		this.membershipPlanService = membershipPlanService;
	}

	@Transactional(readOnly = true)
	public List<ProductSummary> products(GymOsPrincipal principal) {
		return productService.products(principal);
	}

	@Transactional(readOnly = true)
	public ProductSummary product(GymOsPrincipal principal, UUID productId) {
		return productService.product(principal, productId);
	}

	@Transactional
	public ProductSummary createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		return productService.createProduct(principal, request, correlationId);
	}

	@Transactional
	public ProductSummary updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		return productService.updateProduct(principal, productId, request, correlationId);
	}

	@Transactional
	public ProductSummary changeProductStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		return productService.changeStatus(principal, productId, status, correlationId);
	}

	@Transactional
	public void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		productService.archiveProduct(principal, productId, correlationId);
	}

	@Transactional(readOnly = true)
	public List<PlanSummary> plans(GymOsPrincipal principal) {
		return membershipPlanService.plans(principal);
	}

	@Transactional
	public PlanSummary createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
		return membershipPlanService.createPlan(principal, request, correlationId);
	}

	@Transactional
	public PlanSummary createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		return membershipPlanService.createMembershipPlan(principal, request, correlationId);
	}

	@Transactional
	public PlanSummary updateMembershipPlan(GymOsPrincipal principal, UUID planId, UpdateMembershipPlanCommand request, String correlationId) {
		return membershipPlanService.updateMembershipPlan(principal, planId, request, correlationId);
	}
}
