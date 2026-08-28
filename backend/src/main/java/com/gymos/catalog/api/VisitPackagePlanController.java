package com.gymos.catalog.api;

import com.gymos.catalog.application.VisitPackagePlanService;
import com.gymos.catalog.application.command.CreateVisitPackagePlanCommand;
import com.gymos.catalog.application.command.UpdateVisitPackagePlanCommand;
import com.gymos.catalog.application.query.VisitPackagePlanView;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/visit-package-plans")
public class VisitPackagePlanController {
	private final VisitPackagePlanService service;

	public VisitPackagePlanController(VisitPackagePlanService service) {
		this.service = service;
	}

	@GetMapping
	List<VisitPackagePlanView> plans(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.plans(principal);
	}

	@PostMapping
	VisitPackagePlanView create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateVisitPackagePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return service.create(principal, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	@PatchMapping("/{planId}")
	VisitPackagePlanView update(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID planId,
			@Valid @RequestBody UpdateVisitPackagePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return service.update(principal, planId, request.toCommand(), CatalogRequestCorrelation.from(servletRequest));
	}

	public record CreateVisitPackagePlanRequest(
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			@Min(1) int totalVisits,
			@Min(1) int validityDays,
			@NotBlank @Pattern(regexp = "ALL_BRANCHES|SELECTED_BRANCHES") String branchScope,
			@Size(max = 100) List<@NotNull UUID> branchIds,
			@Size(min = 1, max = 100) List<@NotNull UUID> benefitIds,
			Boolean selfServiceAcquisitionEnabled,
			@Pattern(regexp = "DEFAULT|WAIVED") String registrationFeePolicy,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
	) {
		CreateVisitPackagePlanCommand toCommand() {
			return new CreateVisitPackagePlanCommand(
					sku, name, description, priceCents, currency, totalVisits, validityDays, branchScope,
					branchIds, benefitIds, selfServiceAcquisitionEnabled == null || selfServiceAcquisitionEnabled,
					registrationFeePolicy == null ? "DEFAULT" : registrationFeePolicy, status);
		}
	}

	public record UpdateVisitPackagePlanRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			@Min(1) int totalVisits,
			@Min(1) int validityDays,
			@NotBlank @Pattern(regexp = "ALL_BRANCHES|SELECTED_BRANCHES") String branchScope,
			@Size(max = 100) List<@NotNull UUID> branchIds,
			@Size(min = 1, max = 100) List<@NotNull UUID> benefitIds,
			Boolean selfServiceAcquisitionEnabled,
			@Pattern(regexp = "DEFAULT|WAIVED") String registrationFeePolicy,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@NotNull @Min(0) Long expectedVersion
	) {
		UpdateVisitPackagePlanCommand toCommand() {
			return new UpdateVisitPackagePlanCommand(
					name, description, priceCents, currency, totalVisits, validityDays, branchScope,
					branchIds, benefitIds, selfServiceAcquisitionEnabled == null || selfServiceAcquisitionEnabled,
					registrationFeePolicy == null ? "DEFAULT" : registrationFeePolicy, status, expectedVersion);
		}
	}
}
