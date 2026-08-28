package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.query.ProductFamilySummaryView;
import com.gymos.catalog.application.query.ProductFamilyView;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/product-families")
class CatalogProductFamilyController {
	private final CatalogService catalogService;

	CatalogProductFamilyController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	List<ProductFamilySummaryView> families(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.productFamilies(principal);
	}

	@PostMapping("/variants")
	ProductFamilyView createVariants(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateProductVariantsRequest request, HttpServletRequest servletRequest) {
		return catalogService.createProductVariants(
				principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}
}
