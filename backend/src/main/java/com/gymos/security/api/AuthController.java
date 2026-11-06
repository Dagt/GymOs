package com.gymos.security.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.SecurityProperties;
import com.gymos.common.api.BusinessException;
import com.gymos.security.application.AuthService;
import com.gymos.security.application.result.AuthResult;
import com.gymos.security.application.result.CurrentUserResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	static final String REFRESH_COOKIE_PREFIX = "__Host-gymfort-refresh-";
	private final AuthService authService;
	private final SecurityProperties properties;

	public AuthController(AuthService authService, SecurityProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@PostMapping("/login")
	@Operation(parameters = @Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true))
	AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
		requireBrowserRequest(http, response);
		return toResponse(authService.login(request.username(), request.password(), refreshCookie(http), correlationId(http)), http, response);
	}

	@PostMapping("/refresh")
	@Operation(description = "Uses the HttpOnly refresh cookie and returns a rotated cookie with the access token.",
			parameters = {
					@Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true),
					@Parameter(name = "X-GymFort-Refresh-Request", in = ParameterIn.HEADER, required = true)
			})
	AuthResponse refresh(HttpServletRequest http, HttpServletResponse response) {
		requireBrowserRequest(http, response);
		return toResponse(authService.refresh(refreshCookie(http), refreshOperation(http), correlationId(http)), http, response);
	}

	@PostMapping("/logout")
	@Operation(parameters = @Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true))
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest http, HttpServletResponse response) {
		requireBrowserRequest(http, response);
		authService.logout(refreshCookie(http), correlationId(http));
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieName(http), "", Duration.ZERO));
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal GymOsPrincipal principal) {
		return toResponse(authService.currentUser(principal));
	}

	@PostMapping("/select-branch")
	@Operation(parameters = @Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true))
	AuthResponse selectBranch(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectBranchRequest request, HttpServletRequest http, HttpServletResponse response) {
		requireBrowserRequest(http, response);
		return toResponse(authService.selectBranch(principal, request.branchId(), refreshCookie(http), correlationId(http)), http, response);
	}

	@PostMapping("/select-organization")
	@Operation(parameters = @Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true))
	AuthResponse selectOrganization(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectOrganizationRequest request, HttpServletRequest http, HttpServletResponse response) {
		requireBrowserRequest(http, response);
		return toResponse(authService.selectOrganization(principal, request.organizationId(), request.reason(),
				refreshCookie(http), correlationId(http)), http, response);
	}

	@PostMapping("/exit-support")
	@Operation(parameters = @Parameter(name = "X-GymFort-Session", in = ParameterIn.HEADER, required = true))
	AuthResponse exitSupport(@AuthenticationPrincipal GymOsPrincipal principal, HttpServletRequest http,
			HttpServletResponse response) {
		requireBrowserRequest(http, response);
		return toResponse(authService.exitSupport(principal, refreshCookie(http), correlationId(http)), http, response);
	}

	private void requireBrowserRequest(HttpServletRequest request, HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin == null || !properties.corsAllowedOrigins().contains(origin)
				|| headerId(request, "X-GymFort-Session") == null) {
			throw new AccessDeniedException("Invalid browser session origin");
		}
	}

	private UUID refreshOperation(HttpServletRequest request) {
		UUID id = headerId(request, "X-GymFort-Refresh-Request");
		if (id != null) return id;
		throw new BusinessException("REFRESH_REQUEST_INVALID", "No fue posible identificar el intento de renovación.");
	}

	private UUID headerId(HttpServletRequest request, String name) {
		String value = request.getHeader(name);
		try {
			if (value != null && value.length() == 36) return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			// The response never echoes the submitted value.
		}
		return null;
	}

	private String cookieName(HttpServletRequest request) {
		return REFRESH_COOKIE_PREFIX + headerId(request, "X-GymFort-Session");
	}

	private String refreshCookie(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookieName(request).equals(cookie.getName())) return cookie.getValue();
			}
		}
		return null;
	}

	private String cookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value).httpOnly(true).secure(true).sameSite("Strict")
				.path("/").maxAge(maxAge).build().toString();
	}

	private AuthResponse toResponse(AuthResult result, HttpServletRequest request, HttpServletResponse response) {
		Duration remaining = Duration.between(Instant.now(), result.sessionExpiresAt());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieName(request), result.refreshToken(),
				remaining.isNegative() ? Duration.ZERO : remaining));
		return new AuthResponse(result.accessToken(), result.tokenType(), result.expiresIn(), toResponse(result.user()));
	}

	private CurrentUserResponse toResponse(CurrentUserResult result) {
		return new CurrentUserResponse(
				result.userId(),
				result.accountScope(),
				result.organizationId(),
				result.branchId(),
				result.username(),
				result.fullName(),
				result.permissions(),
				result.roles(),
				result.platformAdmin(),
				result.supportMode(),
				result.supportExpiresAt(),
				result.console(),
				result.organizationName(),
				result.brandName(),
				result.logoUrl(),
				result.primaryColor(),
				result.accentColor(),
				result.surfaceColor()
		);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
		@Override
		public String toString() {
			return "LoginRequest[credentials redacted]";
		}
	}

	public record SelectBranchRequest(@NotNull UUID branchId) {
	}

	public record SelectOrganizationRequest(
			@NotNull UUID organizationId,
			@NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
	) {
	}

	public record AuthResponse(
			String accessToken,
			String tokenType,
			long expiresIn,
			CurrentUserResponse user
	) {
		@Override
		public String toString() {
			return "AuthResponse[credentials redacted]";
		}
	}

	public record CurrentUserResponse(
			UUID userId,
			com.gymos.common.security.AccountScope accountScope,
			UUID organizationId,
			UUID branchId,
			String username,
			String fullName,
			Set<String> permissions,
			Set<String> roles,
			boolean platformAdmin,
			boolean supportMode,
			java.time.Instant supportExpiresAt,
			String console,
			String organizationName,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor
	) {
	}
}
