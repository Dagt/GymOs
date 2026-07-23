package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.command.UploadProductImageCommand;
import com.gymos.catalog.application.query.CategoryView;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.common.api.Correlation;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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

	@RequestMapping(value = "/products/{productId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
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

	@PostMapping("/products/{productId}/activate")
	ProductSummaryView activateProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			HttpServletRequest servletRequest
	) {
		return catalogService.changeProductStatus(principal, productId, "ACTIVE", correlationId(servletRequest));
	}

	@PostMapping("/products/{productId}/deactivate")
	ProductSummaryView deactivateProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			HttpServletRequest servletRequest
	) {
		return catalogService.changeProductStatus(principal, productId, "INACTIVE", correlationId(servletRequest));
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

	@PostMapping(value = "/products/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ProductSummaryView uploadProductImage(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest servletRequest
	) {
		try {
			return catalogService.uploadProductImage(
					principal,
					productId,
					new UploadProductImageCommand(
							file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()
					),
					correlationId(servletRequest)
			);
		} catch (IOException exception) {
			throw new BusinessException("PRODUCT_IMAGE_INVALID", "No fue posible leer la imagen.");
		}
	}

	@DeleteMapping("/products/{productId}/image")
	ProductSummaryView removeProductImage(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId,
			HttpServletRequest servletRequest
	) {
		return catalogService.removeProductImage(principal, productId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

}
