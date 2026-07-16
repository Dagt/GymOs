package com.gymos.onboarding.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.onboarding.application.OnboardingService;
import com.gymos.onboarding.application.command.ApplyPilotSetupCommand;
import com.gymos.onboarding.application.result.OnboardingChecklistResult;
import com.gymos.onboarding.application.result.PilotSetupResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@GetMapping("/checklist")
	OnboardingChecklistResult checklist(@AuthenticationPrincipal GymOsPrincipal principal) {
		return onboardingService.checklist(principal);
	}

	@PostMapping("/pilot-setup")
	PilotSetupResult applyPilotSetup(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody PilotSetupRequest request,
			HttpServletRequest servletRequest
	) {
		return onboardingService.applyPilotSetup(principal, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record PilotSetupRequest(
			@Valid OrganizationProfileRequest organization,
			@Valid BranchProfileRequest branch,
			List<@Valid PilotPlanRequest> plans,
			List<@Valid PilotProductRequest> products,
			@Size(max = 80) String kioskTerminalCode,
			@Size(max = 120) String kioskName
	) {
		private ApplyPilotSetupCommand toCommand() {
			return new ApplyPilotSetupCommand(
					organization == null ? null : organization.toCommand(),
					branch == null ? null : branch.toCommand(),
					plans == null ? List.of() : plans.stream().map(PilotPlanRequest::toCommand).toList(),
					products == null ? List.of() : products.stream().map(PilotProductRequest::toCommand).toList(),
					kioskTerminalCode,
					kioskName
			);
		}
	}

	public record OrganizationProfileRequest(
			@NotBlank @Size(max = 180) String legalName,
			@NotBlank @Size(max = 120) String tradeName,
			@Size(max = 64) String taxId,
			@NotBlank @Size(max = 64) String timezone,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrency,
			@Email @Size(max = 180) String contactEmail,
			@Size(max = 32) String contactPhone
	) {
		private ApplyPilotSetupCommand.OrganizationProfile toCommand() {
			return new ApplyPilotSetupCommand.OrganizationProfile(
					legalName,
					tradeName,
					taxId,
					timezone,
					defaultCurrency,
					contactEmail,
					contactPhone
			);
		}
	}

	public record BranchProfileRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 32) String code,
			@NotBlank @Size(max = 64) String timezone,
			@Size(max = 32) String phone
	) {
		private ApplyPilotSetupCommand.BranchProfile toCommand() {
			return new ApplyPilotSetupCommand.BranchProfile(name, code, timezone, phone);
		}
	}

	public record PilotPlanRequest(
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			@Min(1) int durationDays,
			@Min(0) int graceDays,
			@Size(max = 4000) String benefitsJson
	) {
		private ApplyPilotSetupCommand.PilotPlan toCommand() {
			return new ApplyPilotSetupCommand.PilotPlan(
					sku,
					name,
					description,
					priceCents,
					currency,
					durationDays,
					graceDays,
					benefitsJson
			);
		}
	}

	public record PilotProductRequest(
			@NotBlank @Size(max = 64) String sku,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@NotBlank @Pattern(regexp = "PHYSICAL|SERVICE") String productType,
			@Min(0) long priceCents,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
			boolean taxable
	) {
		private ApplyPilotSetupCommand.PilotProduct toCommand() {
			return new ApplyPilotSetupCommand.PilotProduct(
					sku,
					name,
					description,
					productType,
					priceCents,
					currency,
					taxable
			);
		}
	}
}
