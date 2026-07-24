package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
class CatalogPlanController {
	private final CatalogService catalogService;

	CatalogPlanController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/plans")
	List<PlanView> plans(@AuthenticationPrincipal GymOsPrincipal principal) {
		return catalogService.plans(principal);
	}

	@PostMapping("/plans")
	PlanView createPlan(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreatePlanRequest request, HttpServletRequest servletRequest) {
		return catalogService.createPlan(principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PostMapping("/membership-plans")
	PlanView createMembershipPlan(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateMembershipPlanRequest request, HttpServletRequest servletRequest) {
		return catalogService.createMembershipPlan(principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PatchMapping("/plans/{planId}")
	PlanView updateMembershipPlan(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID planId,
			@Valid @RequestBody UpdateMembershipPlanRequest request, HttpServletRequest servletRequest) {
		return catalogService.updateMembershipPlan(principal, planId, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}
}
