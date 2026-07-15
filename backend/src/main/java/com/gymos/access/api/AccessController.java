package com.gymos.access.api;

import com.gymos.access.application.AccessService;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.AccessLogRecord;
import com.gymos.access.infrastructure.AccessRepository.TemporaryPassRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {
	private final AccessService accessService;

	public AccessController(AccessService accessService) {
		this.accessService = accessService;
	}

	@GetMapping("/devices")
	List<AccessDeviceRecord> devices(@AuthenticationPrincipal GymOsPrincipal principal) {
		return accessService.devices(principal);
	}

	@GetMapping("/logs")
	List<AccessLogRecord> logs(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "30") int limit
	) {
		return accessService.logs(principal, limit);
	}

	@PostMapping("/requests")
	AccessDecisionResponse requestAccess(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody AccessRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.requestAccess(principal, request, correlationId(servletRequest));
	}

	@PostMapping("/commands/open")
	AccessDecisionResponse manualOpen(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ManualOpenRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.manualOpen(principal, request, correlationId(servletRequest));
	}

	@PostMapping("/commands/temporary")
	TemporaryPassRecord temporaryPass(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody TemporaryPassRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.createTemporaryPass(principal, request, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record AccessRequest(
			UUID clientId,
			@Size(max = 180) String identifier,
			@Size(max = 80) String deviceCode
	) {
	}

	public record ManualOpenRequest(
			UUID clientId,
			@Size(max = 80) String deviceCode,
			@NotBlank @Size(max = 500) String reason
	) {
	}

	public record TemporaryPassRequest(
			@NotNull UUID clientId,
			OffsetDateTime startsAt,
			@NotNull OffsetDateTime expiresAt,
			@Min(1) int maxUses,
			@NotBlank @Size(max = 500) String reason
	) {
	}
}
