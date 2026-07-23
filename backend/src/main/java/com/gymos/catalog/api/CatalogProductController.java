package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/products")
class CatalogProductController {
	private final CatalogService catalogService;

	CatalogProductController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	List<ProductSummaryView> products(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.products(principal);
	}

	@GetMapping("/{productId}")
	ProductSummaryView product(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId) {
		return catalogService.product(principal, productId);
	}

	@GetMapping("/search")
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
		return catalogService.searchProducts(principal,
				new ProductSearchCriteria(q, type, categoryId, status, inventoryTracked, includeArchived, page, size, sort));
	}

	@PostMapping
	ProductSummaryView createProduct(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ProductRequest request, HttpServletRequest servletRequest) {
		return catalogService.createProduct(principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@RequestMapping(value = "/{productId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	ProductSummaryView updateProduct(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			@Valid @RequestBody ProductRequest request, HttpServletRequest servletRequest) {
		return catalogService.updateProduct(principal, productId, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PatchMapping("/{productId}/status")
	ProductSummaryView changeProductStatus(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			@Valid @RequestBody ProductStatusRequest request, HttpServletRequest servletRequest) {
		return catalogService.changeProductStatus(principal, productId, request.status(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PostMapping("/{productId}/activate")
	ProductSummaryView activateProduct(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			HttpServletRequest servletRequest) {
		return catalogService.changeProductStatus(principal, productId, "ACTIVE", CatalogRequestCorrelation.from(servletRequest));
	}

	@PostMapping("/{productId}/deactivate")
	ProductSummaryView deactivateProduct(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			HttpServletRequest servletRequest) {
		return catalogService.changeProductStatus(principal, productId, "INACTIVE", CatalogRequestCorrelation.from(servletRequest));
	}

	@PostMapping("/{productId}/restore")
	ProductSummaryView restoreProduct(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			HttpServletRequest servletRequest) {
		return catalogService.restoreProduct(principal, productId, CatalogRequestCorrelation.from(servletRequest));
	}

	@DeleteMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archiveProduct(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			HttpServletRequest servletRequest) {
		catalogService.archiveProduct(principal, productId, CatalogRequestCorrelation.from(servletRequest));
	}
}
