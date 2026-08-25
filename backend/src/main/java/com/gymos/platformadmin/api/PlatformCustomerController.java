package com.gymos.platformadmin.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformadmin.application.PlatformCustomerService;
import com.gymos.platformadmin.application.PlatformCustomerProvisioningService;
import com.gymos.platformadmin.application.PlatformCustomerService.CustomerPortfolioView;
import com.gymos.platformadmin.application.PlatformCustomerService.CreateCustomerCommand;
import com.gymos.platformadmin.application.PlatformCustomerService.PlatformCustomerView;
import com.gymos.platformadmin.application.PlatformCustomerService.ProvisionedCustomer;
import com.gymos.platformadmin.application.PlatformCustomerService.ProvisionedOwnerInvitation;
import com.gymos.platformadmin.application.PlatformCustomerService.UpdateCustomerAccountCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/platform/customers")
public class PlatformCustomerController {
	private static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";
	private static final String MX_RFC = "^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$";
	private static final String MX_PHONE = "^\\+52\\d{10}$";
	private final PlatformCustomerService service;
	private final PlatformCustomerProvisioningService provisioningService;

	public PlatformCustomerController(
			PlatformCustomerService service,
			PlatformCustomerProvisioningService provisioningService
	) {
		this.service = service;
		this.provisioningService = provisioningService;
	}

	@GetMapping
	CustomerPortfolioView customers(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.portfolio(principal);
	}

	@PostMapping
	ProvisionedCustomer create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateCustomerRequest request,
			HttpServletRequest servletRequest
	) {
		return provisioningService.create(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/{organizationId}/owner-invitation")
	ProvisionedOwnerInvitation reissueOwnerInvitation(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID organizationId,
			HttpServletRequest servletRequest
	) {
		return provisioningService.reissueOwnerInvitation(
				principal, organizationId, correlationId(servletRequest));
	}

	@PutMapping("/{organizationId}")
	PlatformCustomerView update(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID organizationId,
			@Valid @RequestBody CustomerAccountRequest request,
			HttpServletRequest servletRequest
	) {
		return service.update(principal, organizationId, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CustomerAccountRequest(
			@NotBlank String lifecycleStatus,
			@Size(max = 120) String accountOwner,
			@Email @Size(max = 254) String billingEmail,
			@Size(max = 32) String billingPhone,
			LocalDate contractStartedOn,
			LocalDate renewalOn,
			@Min(0) @Max(90) int paymentTermsDays,
			LocalDate nextFollowUpOn,
			@Size(max = 2000) String notes,
			@Min(0) long expectedVersion
	) {
		private UpdateCustomerAccountCommand toCommand() {
			return new UpdateCustomerAccountCommand(
					lifecycleStatus, accountOwner, billingEmail, billingPhone,
					contractStartedOn, renewalOn, paymentTermsDays,
					nextFollowUpOn, notes, expectedVersion);
		}
	}

	public record CreateCustomerRequest(
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
			@NotBlank @Size(max = 120) String branchName,
			@NotBlank @Size(max = 32) String branchCode,
			@NotBlank @Pattern(regexp = MX_PHONE) String branchPhone,
			@NotBlank @Size(max = 120) String ownerFirstName,
			@NotBlank @Size(max = 120) String ownerLastName,
			@NotBlank @Email @Size(max = 180) String ownerEmail,
			@NotBlank @Pattern(regexp = MX_PHONE) String ownerPhone
	) {
		private CreateCustomerCommand toCommand() {
			return new CreateCustomerCommand(
					legalName, tradeName, taxId, timezone, defaultCurrency, contactEmail, contactPhone,
					brandName, logoUrl, primaryColor, accentColor, surfaceColor,
					branchName, branchCode, branchPhone,
					ownerFirstName, ownerLastName, ownerEmail, ownerPhone);
		}
	}
}
