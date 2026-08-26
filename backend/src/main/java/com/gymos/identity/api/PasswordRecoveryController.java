package com.gymos.identity.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.ClientIpResolver;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import com.gymos.identity.application.PasswordRecoveryProperties;
import com.gymos.identity.application.PasswordRecoveryService;
import com.gymos.identity.application.UserInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PasswordRecoveryController {
	private final PasswordRecoveryService service;
	private final RateLimiter limiter;
	private final ClientIpResolver ipResolver;
	private final PasswordRecoveryProperties properties;
	public PasswordRecoveryController(PasswordRecoveryService service, RateLimiter limiter, ClientIpResolver ipResolver, PasswordRecoveryProperties properties) {
		this.service = service; this.limiter = limiter; this.ipResolver = ipResolver; this.properties = properties;
	}

	@PostMapping("/users/internal/{userId}/password-recovery")
	ResponseEntity<PasswordRecoveryService.RecoveryIssued> issue(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID userId, HttpServletRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.issue(principal, userId, correlation(request)));
	}

	@PostMapping("/auth/password-recovery/accept")
	ResponseEntity<Map<String, String>> consume(@Valid @RequestBody RecoveryRequest body, HttpServletRequest request) {
		limiter.check("password-recovery:" + UserInvitationService.sha256(ipResolver.resolve(request)),
				properties.maxAttemptsPerMinute(), Duration.ofMinutes(1));
		service.consume(body.token(), body.password(), correlation(request));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status", "PASSWORD_CHANGED"));
	}

	private String correlation(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}
	public record RecoveryRequest(@NotBlank @Size(max = 512) String token, @NotBlank @Size(max = 120) String password) {
		@Override public String toString() { return "RecoveryRequest[REDACTED]"; }
	}
}
