package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
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
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/products")
	List<ProductSummaryView> products(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.products(principal);
	}

	@GetMapping("/products/{productId}")
	ProductSummaryView product(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId) {
		return catalogService.product(principal, productId);
	}

	@GetMapping("/products/search")
	ProductPageView searchProducts(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean inventoryTracked,
			@RequestParam(defaultValue = "false") boolean includeArchived,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) int size,
			@RequestParam(defaultValue = "displayOrder,asc") String sort
	) {
		return catalogService.searchProducts(
				principal,
				new ProductSearchCriteria(
						q, type, categoryId, status, inventoryTracked, includeArchived, page, size, sort
				)
		);
	}

	@PostMapping("/products")
	ProductSummaryView createProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ProductRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createProduct(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PutMapping("/products/{productId}")
	ProductSummaryView updateProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			@Valid @RequestBody ProductRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.updateProduct(principal, productId, request.toCommand(), correlationId(servletRequest));
	}

	@PatchMapping("/products/{productId}/status")
	ProductSummaryView changeProductStatus(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			@Valid @RequestBody ProductStatusRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.changeProductStatus(principal, productId, request.status(), correlationId(servletRequest));
	}

	@DeleteMapping("/products/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archiveProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			HttpServletRequest servletRequest
	) {
		catalogService.archiveProduct(principal, productId, correlationId(servletRequest));
	}

	@GetMapping("/plans")
	List<PlanView> plans(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.plans(principal);
	}

	@PostMapping("/plans")
	PlanView createPlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreatePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createPlan(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/membership-plans")
	PlanView createMembershipPlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateMembershipPlanRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createMembershipPlan(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PatchMapping("/plans/{planId}")
	PlanView updateMembershipPlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID planId,
			@Valid @RequestBody UpdateMembershipPlanRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.updateMembershipPlan(principal, planId, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/categories")
	List<CategoryView> categories(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "false") boolean includeInactive
	) {
		return catalogService.categories(principal, includeInactive);
	}

	@PostMapping("/categories")
	CategoryView createCategory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CategoryRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createCategory(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PatchMapping("/categories/{categoryId}")
	CategoryView updateCategory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID categoryId,
			@Valid @RequestBody CategoryRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.updateCategory(principal, categoryId, request.toCommand(), correlationId(servletRequest));
	}

	@DeleteMapping("/categories/{categoryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archiveCategory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID categoryId,
			HttpServletRequest servletRequest
	) {
		catalogService.archiveCategory(principal, categoryId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record ProductRequest(
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@NotBlank @Pattern(regexp = "PHYSICAL|SERVICE|MEMBERSHIP_PLAN") String productType,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			boolean taxable,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@Size(max = 128) String barcode,
			@Size(max = 160) String brand,
			@Size(max = 160) String presentation,
			@Size(max = 40) String unitOfMeasure,
			boolean inventoryTracked,
			@Min(0) int displayOrder,
			@Size(max = 500) String imageObjectKey,
			UUID categoryId
	) {
		private ProductCommand toCommand() {
			return new ProductCommand(
					sku, name, description, productType, priceCents, currency, taxable, status,
					barcode, brand, presentation, unitOfMeasure, inventoryTracked, displayOrder,
					imageObjectKey, categoryId
			);
		}
	}

	public record ProductStatusRequest(
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
	}

	public record CategoryRequest(
			UUID parentId,
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@Min(0) int displayOrder
	) {
		private CategoryCommand toCommand() {
			return new CategoryCommand(parentId, name, description, status, displayOrder);
		}
	}

	public record CreatePlanRequest(
			@NotNull UUID productId,
			@Min(1) int durationDays,
			@Min(0) int graceDays,
			@Size(max = 4000) String benefitsJson,
			boolean multiBranchAccess,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
		private CreatePlanCommand toCommand() {
			return new CreatePlanCommand(productId, durationDays, graceDays, benefitsJson, multiBranchAccess, status);
		}
	}

	public record CreateMembershipPlanRequest(
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			@Min(1) int durationDays,
			@Min(0) int graceDays,
			@Size(max = 4000) String benefits,
			boolean multiBranchAccess,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
		private CreateMembershipPlanCommand toCommand() {
			return new CreateMembershipPlanCommand(
					sku, name, description, priceCents, currency, durationDays, graceDays,
					benefits, multiBranchAccess, status
			);
		}
	}

	public record UpdateMembershipPlanRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			@Min(1) int durationDays,
			@Min(0) int graceDays,
			@Size(max = 4000) String benefits,
			boolean multiBranchAccess,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
		private UpdateMembershipPlanCommand toCommand() {
			return new UpdateMembershipPlanCommand(
					name, description, priceCents, currency, durationDays, graceDays,
					benefits, multiBranchAccess, status
			);
		}
	}
}
