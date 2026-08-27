package com.gymos.clients.api;

import com.gymos.clients.application.ClientLifecycleService;
import com.gymos.clients.application.ClientLifecycleService.UpdateClientLifecycleConfiguration;
import com.gymos.clients.infrastructure.ClientLifecycleRepository.ClientLifecycleConfiguration;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-lifecycle-settings")
public class ClientLifecycleController {
	private final ClientLifecycleService service;

	public ClientLifecycleController(ClientLifecycleService service) {
		this.service = service;
	}

	@GetMapping
	ClientLifecycleConfiguration configuration(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.configuration(principal);
	}

	@PutMapping
	ClientLifecycleConfiguration update(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody UpdateClientLifecycleRequest request,
			HttpServletRequest servletRequest
	) {
		Object correlationId = servletRequest.getAttribute(Correlation.ATTRIBUTE);
		return service.update(principal, request.toCommand(), correlationId == null ? "unknown" : correlationId.toString());
	}

	public record UpdateClientLifecycleRequest(
			boolean registrationFeeEnabled,
			@Min(0) long registrationFeeCents,
			@Size(max = 120) String registrationFeeLabel,
			boolean autoInactivationEnabled,
			@Min(1) @Max(120) int inactivityMonths
	) {
		UpdateClientLifecycleConfiguration toCommand() {
			return new UpdateClientLifecycleConfiguration(
					registrationFeeEnabled, registrationFeeCents, registrationFeeLabel,
					autoInactivationEnabled, inactivityMonths
			);
		}
	}
}
