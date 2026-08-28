package com.gymos.benefits.api;

import com.gymos.benefits.application.BenefitService;
import com.gymos.benefits.application.command.AssignClientBenefitCommand;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.benefits.application.command.UpdateBenefitCommand;
import com.gymos.benefits.infrastructure.BenefitRepository.BenefitSummary;
import com.gymos.benefits.infrastructure.BenefitRepository.ClientBenefitSummary;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BenefitController {
	private final BenefitService benefitService;

	public BenefitController(BenefitService benefitService) {
		this.benefitService = benefitService;
	}

	@GetMapping("/benefits")
	List<BenefitSummary> benefits(@AuthenticationPrincipal GymOsPrincipal principal) {
		return benefitService.benefits(principal);
	}

	@GetMapping("/clients/{clientId}/benefits")
	List<ClientBenefitSummary> clientBenefits(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		return benefitService.clientBenefits(principal, clientId);
	}

	@PostMapping("/benefits")
	BenefitSummary createBenefit(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateBenefitRequest request,
			HttpServletRequest servletRequest
	) {
		return benefitService.createBenefit(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/clients/{clientId}/benefits")
	ClientBenefitSummary assignClientBenefit(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId,
			@Valid @RequestBody AssignClientBenefitRequest request,
			HttpServletRequest servletRequest
	) {
		return benefitService.assignToClient(principal, request.toCommand(clientId), correlationId(servletRequest));
	}

	@PutMapping("/benefits/{benefitId}")
	BenefitSummary updateBenefit(
			@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID benefitId,
			@Valid @RequestBody UpdateBenefitRequest request, HttpServletRequest servletRequest
	) {
		return benefitService.updateBenefit(principal, benefitId, new UpdateBenefitCommand(
				request.name(), request.discountType(), request.discountValueCents(), request.discountPercent(),
				request.requiresEvidence(), request.combinable(), request.priority(), request.validFrom(),
				request.validUntil(), request.status(), request.planIds(), request.accessScope(),
				request.applicablePlanIds(), request.expectedVersion()), correlationId(servletRequest));
	}

	public record UpdateBenefitRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Pattern(regexp = "PERCENT|FIXED|SPECIAL_PRICE|NONE") String discountType,
			@Min(0) Integer discountValueCents, @Min(0) @Max(100) Double discountPercent,
			boolean requiresEvidence, boolean combinable, @Min(0) int priority,
			LocalDate validFrom, LocalDate validUntil,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@Size(max = 100) List<@NotNull UUID> planIds,
			@Pattern(regexp = "LOCAL|PLAN_COVERAGE") String accessScope,
			@Size(max = 100) List<@NotNull UUID> applicablePlanIds,
			@NotNull @Min(0) Long expectedVersion
	) {}

	@PostMapping("/client-benefits/{clientBenefitId}/verify")
	ClientBenefitSummary verifyClientBenefit(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientBenefitId,
			HttpServletRequest servletRequest
	) {
		return benefitService.verify(principal, clientBenefitId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateBenefitRequest(
			UUID branchId,
			@NotBlank @Size(max = 80) String code,
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Pattern(regexp = "STUDENT|SENIOR|AGREEMENT|PROMOTION|ACCESS|CUSTOM") String benefitType,
			@NotBlank @Pattern(regexp = "PERCENT|FIXED|SPECIAL_PRICE|NONE") String discountType,
			@Min(0) Integer discountValueCents,
			@Min(0) @Max(100) Double discountPercent,
			boolean requiresEvidence,
			boolean combinable,
			@Min(0) int priority,
			LocalDate validFrom,
			LocalDate validUntil,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@Size(max = 100) List<@NotNull UUID> planIds,
			@Pattern(regexp = "LOCAL|PLAN_COVERAGE") String accessScope,
			@Size(max = 100) List<@NotNull UUID> applicablePlanIds
	) {
		CreateBenefitCommand toCommand() {
			return new CreateBenefitCommand(branchId, code, name, benefitType, discountType, discountValueCents,
					discountPercent, requiresEvidence, combinable, priority, validFrom, validUntil, status, planIds,
					accessScope, applicablePlanIds);
		}
	}

	public record AssignClientBenefitRequest(
			@NotNull UUID benefitId,
			UUID branchId,
			LocalDate validFrom,
			LocalDate validUntil,
			@Size(max = 1000) String notes
	) {
		AssignClientBenefitCommand toCommand(UUID clientId) {
			return new AssignClientBenefitCommand(clientId, benefitId, branchId, validFrom, validUntil, notes);
		}
	}
}
