package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/products")
	List<ProductSummary> products(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.products(principal);
	}

	@PostMapping("/products")
	ProductSummary createProduct(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ProductRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createProduct(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/plans")
	List<PlanSummary> plans(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.plans(principal);
	}

	@PostMapping("/plans")
	PlanSummary createPlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreatePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createPlan(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/membership-plans")
	PlanSummary createMembershipPlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateMembershipPlanRequest request,
			HttpServletRequest servletRequest
	) {
		return catalogService.createMembershipPlan(principal, request.toCommand(), correlationId(servletRequest));
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
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
		private ProductCommand toCommand() {
			return new ProductCommand(sku, name, description, productType, priceCents, currency, taxable, status);
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
					sku,
					name,
					description,
					priceCents,
					currency,
					durationDays,
					graceDays,
					benefits,
					multiBranchAccess,
					status
			);
		}
	}
}
