package com.gymos.catalog.application;

import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.command.UpdateMembershipPlanCommand;
import com.gymos.catalog.application.command.CategoryCommand;
import com.gymos.catalog.application.query.CategoryView;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final CatalogProductService productService;
	private final CatalogMembershipPlanService membershipPlanService;
	private final CatalogCategoryService categoryService;

	public CatalogService(
			CatalogProductService productService,
			CatalogMembershipPlanService membershipPlanService,
			CatalogCategoryService categoryService
	) {
		this.productService = productService;
		this.membershipPlanService = membershipPlanService;
		this.categoryService = categoryService;
	}

	@Transactional(readOnly = true)
	public List<ProductSummaryView> products(GymOsPrincipal principal) {
		return productService.products(principal);
	}

	@Transactional(readOnly = true)
	public ProductSummaryView product(GymOsPrincipal principal, UUID productId) {
		return productService.product(principal, productId);
	}

	@Transactional(readOnly = true)
	public ProductPageView searchProducts(GymOsPrincipal principal, ProductSearchCriteria criteria) {
		return productService.search(principal, criteria);
	}

	@Transactional
	public ProductSummaryView createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		return productService.createProduct(principal, request, correlationId);
	}

	@Transactional
	public ProductSummaryView updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		return productService.updateProduct(principal, productId, request, correlationId);
	}

	@Transactional
	public ProductSummaryView changeProductStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		return productService.changeStatus(principal, productId, status, correlationId);
	}

	@Transactional
	public void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		productService.archiveProduct(principal, productId, correlationId);
	}

	@Transactional(readOnly = true)
	public List<PlanView> plans(GymOsPrincipal principal) {
		return membershipPlanService.plans(principal);
	}

	@Transactional
	public PlanView createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
		return membershipPlanService.createPlan(principal, request, correlationId);
	}

	@Transactional
	public PlanView createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		return membershipPlanService.createMembershipPlan(principal, request, correlationId);
	}

	@Transactional
	public PlanView updateMembershipPlan(GymOsPrincipal principal, UUID planId, UpdateMembershipPlanCommand request, String correlationId) {
		return membershipPlanService.updateMembershipPlan(principal, planId, request, correlationId);
	}

	@Transactional(readOnly = true)
	public List<CategoryView> categories(GymOsPrincipal principal, boolean includeInactive) {
		return categoryService.categories(principal, includeInactive);
	}

	@Transactional
	public CategoryView createCategory(GymOsPrincipal principal, CategoryCommand command, String correlationId) {
		return categoryService.create(principal, command, correlationId);
	}

	@Transactional
	public CategoryView updateCategory(
			GymOsPrincipal principal,
			UUID categoryId,
			CategoryCommand command,
			String correlationId
	) {
		return categoryService.update(principal, categoryId, command, correlationId);
	}

	@Transactional
	public void archiveCategory(GymOsPrincipal principal, UUID categoryId, String correlationId) {
		categoryService.archive(principal, categoryId, correlationId);
	}
}
