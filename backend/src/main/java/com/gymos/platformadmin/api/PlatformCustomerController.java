package com.gymos.platformadmin.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformadmin.application.PlatformCustomerService;
import com.gymos.platformadmin.application.PlatformCustomerService.CustomerPortfolioView;
import com.gymos.platformadmin.application.PlatformCustomerService.PlatformCustomerView;
import com.gymos.platformadmin.application.PlatformCustomerService.UpdateCustomerAccountCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/customers")
public class PlatformCustomerController {
	private final PlatformCustomerService service;

	public PlatformCustomerController(PlatformCustomerService service) {
		this.service = service;
	}

	@GetMapping
	CustomerPortfolioView customers(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.portfolio(principal);
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
}
