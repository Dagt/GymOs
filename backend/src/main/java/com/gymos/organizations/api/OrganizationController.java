package com.gymos.organizations.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.OrganizationService;
import com.gymos.organizations.application.command.CreateBranchCommand;
import com.gymos.organizations.application.command.CreateOrganizationCommand;
import com.gymos.organizations.application.command.UpdateBranchCommand;
import com.gymos.organizations.application.command.UpdateOrganizationCommand;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.BranchSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.ManagedOrganizationSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
	private final OrganizationService organizationService;
	private static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";
	private static final String MX_RFC = "^[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3}$";
	private static final String MX_PHONE = "^\\+52\\d{10}$";

	public OrganizationController(OrganizationService organizationService) {
		this.organizationService = organizationService;
	}

	@GetMapping("/current")
	OrganizationSummary current(@AuthenticationPrincipal GymOsPrincipal principal) {
		return organizationService.current(principal);
	}

	@GetMapping
	List<ManagedOrganizationSummary> organizations(@AuthenticationPrincipal GymOsPrincipal principal) {
		return organizationService.organizations(principal);
	}

	@PostMapping
	ManagedOrganizationSummary createOrganization(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody OrganizationCreateRequest request,
			HttpServletRequest servletRequest
	) {
		return organizationService.createOrganization(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/current/branches")
	List<BranchSummary> branches(@AuthenticationPrincipal GymOsPrincipal principal) {
		return organizationService.branches(principal);
	}

	@GetMapping("/{organizationId}/branches")
	List<BranchSummary> organizationBranches(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID organizationId
	) {
		return organizationService.branchesForOrganization(principal, organizationId);
	}

	@PutMapping("/current")
	OrganizationSummary updateOrganization(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody OrganizationUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		return organizationService.updateOrganization(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/current/branches")
	BranchSummary createBranch(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody BranchCreateRequest request,
			HttpServletRequest servletRequest
	) {
		return organizationService.createBranch(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PutMapping("/current/branches/{branchId}")
	BranchSummary updateBranch(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID branchId,
			@Valid @RequestBody BranchUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		return organizationService.updateBranch(principal, branchId, request.toCommand(), correlationId(servletRequest));
	}

	@PatchMapping("/{organizationId}/status")
	ManagedOrganizationSummary updateOrganizationStatus(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID organizationId,
			@Valid @RequestBody OrganizationStatusRequest request,
			HttpServletRequest servletRequest
	) {
		return organizationService.updateOrganizationStatus(principal, organizationId, request.status(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record OrganizationUpdateRequest(
			@NotBlank @Size(max = 180) String legalName,
			@NotBlank @Size(max = 120) String tradeName,
			@NotBlank @Pattern(regexp = MX_RFC) String taxId,
			@NotBlank @Size(max = 64) String timezone,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrency,
			@NotBlank @Email @Size(max = 180) String contactEmail,
			@NotBlank @Pattern(regexp = MX_PHONE) String contactPhone,
			@Size(max = 120) String brandName,
			@Size(max = 500) String logoUrl,
			@Pattern(regexp = HEX_COLOR) String primaryColor,
			@Pattern(regexp = HEX_COLOR) String accentColor,
			@Pattern(regexp = HEX_COLOR) String surfaceColor
	) {
		private UpdateOrganizationCommand toCommand() {
			return new UpdateOrganizationCommand(
					legalName,
					tradeName,
					taxId,
					timezone,
					defaultCurrency,
					contactEmail,
					contactPhone,
					brandName,
					logoUrl,
					primaryColor,
					accentColor,
					surfaceColor
			);
		}
	}

	public record OrganizationCreateRequest(
			@NotBlank @Size(max = 180) String legalName,
			@NotBlank @Size(max = 120) String tradeName,
			@NotBlank @Pattern(regexp = MX_RFC) String taxId,
			@NotBlank @Size(max = 64) String timezone,
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrency,
			@NotBlank @Email @Size(max = 180) String contactEmail,
			@NotBlank @Pattern(regexp = MX_PHONE) String contactPhone,
			@Size(max = 120) String brandName,
			@Size(max = 500) String logoUrl,
			@Pattern(regexp = HEX_COLOR) String primaryColor,
			@Pattern(regexp = HEX_COLOR) String accentColor,
			@Pattern(regexp = HEX_COLOR) String surfaceColor,
			@Size(max = 120) String firstBranchName,
			@Size(max = 32) String firstBranchCode,
			@Pattern(regexp = MX_PHONE) String firstBranchPhone
	) {
		private CreateOrganizationCommand toCommand() {
			return new CreateOrganizationCommand(
					legalName,
					tradeName,
					taxId,
					timezone,
					defaultCurrency,
					contactEmail,
					contactPhone,
					brandName,
					logoUrl,
					primaryColor,
					accentColor,
					surfaceColor,
					firstBranchName,
					firstBranchCode,
					firstBranchPhone
			);
		}
	}

	public record BranchUpdateRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 32) String code,
			@NotBlank @Size(max = 64) String timezone,
			@NotBlank @Pattern(regexp = MX_PHONE) String phone,
			@Size(max = 120) String brandName,
			@Size(max = 512) String logoUrl,
			@Pattern(regexp = HEX_COLOR) String primaryColor,
			@Pattern(regexp = HEX_COLOR) String accentColor,
			@Pattern(regexp = HEX_COLOR) String surfaceColor
	) {
		private UpdateBranchCommand toCommand() {
			return new UpdateBranchCommand(name, code, timezone, phone, brandName, logoUrl, primaryColor, accentColor, surfaceColor);
		}
	}

	public record BranchCreateRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 32) String code,
			@NotBlank @Size(max = 64) String timezone,
			@NotBlank @Pattern(regexp = MX_PHONE) String phone,
			@Size(max = 120) String brandName,
			@Size(max = 512) String logoUrl,
			@Pattern(regexp = HEX_COLOR) String primaryColor,
			@Pattern(regexp = HEX_COLOR) String accentColor,
			@Pattern(regexp = HEX_COLOR) String surfaceColor
	) {
		private CreateBranchCommand toCommand() {
			return new CreateBranchCommand(name, code, timezone, phone, brandName, logoUrl, primaryColor, accentColor, surfaceColor);
		}
	}

	public record OrganizationStatusRequest(
			@NotBlank String status
	) {
	}
}
