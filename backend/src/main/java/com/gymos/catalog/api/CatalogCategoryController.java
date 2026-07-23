package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.query.CategoryView;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/categories")
class CatalogCategoryController {
	private final CatalogService catalogService;

	CatalogCategoryController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	List<CategoryView> categories(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return catalogService.categories(principal, includeInactive);
	}

	@PostMapping
	CategoryView createCategory(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CategoryRequest request, HttpServletRequest servletRequest) {
		return catalogService.createCategory(principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PatchMapping("/{categoryId}")
	CategoryView updateCategory(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID categoryId,
			@Valid @RequestBody CategoryRequest request, HttpServletRequest servletRequest) {
		return catalogService.updateCategory(principal, categoryId, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@DeleteMapping("/{categoryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archiveCategory(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID categoryId,
			HttpServletRequest servletRequest) {
		catalogService.archiveCategory(principal, categoryId, CatalogRequestCorrelation.from(servletRequest));
	}
}
