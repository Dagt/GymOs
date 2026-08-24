package com.gymos.security.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.security.application.AuthService;
import com.gymos.security.application.result.AuthResult;
import com.gymos.security.application.result.CurrentUserResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return toResponse(authService.login(request.username(), request.password(), correlationId(servletRequest)));
	}

	@PostMapping("/refresh")
	AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return toResponse(authService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	void logout(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody LogoutRequest request,
			HttpServletRequest servletRequest
	) {
		authService.logout(principal, request.refreshToken(), correlationId(servletRequest));
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal GymOsPrincipal principal) {
		return toResponse(authService.currentUser(principal));
	}

	@PostMapping("/select-branch")
	AuthResponse selectBranch(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectBranchRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(authService.selectBranch(principal, request.branchId(), correlationId(servletRequest)));
	}

	@PostMapping("/select-organization")
	AuthResponse selectOrganization(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectOrganizationRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(authService.selectOrganization(
				principal, request.organizationId(), request.reason(), correlationId(servletRequest)));
	}

	@PostMapping("/exit-support")
	AuthResponse exitSupport(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ExitSupportRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(authService.exitSupport(
				principal, request.refreshToken(), correlationId(servletRequest)));
	}

	private AuthResponse toResponse(AuthResult result) {
		return new AuthResponse(
				result.accessToken(),
				result.refreshToken(),
				result.tokenType(),
				result.expiresIn(),
				toResponse(result.user())
		);
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
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record LogoutRequest(@NotBlank String refreshToken) {
	}

	public record SelectBranchRequest(@NotNull UUID branchId) {
	}

	public record SelectOrganizationRequest(
			@NotNull UUID organizationId,
			@NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
	) {
	}

	public record ExitSupportRequest(@NotBlank String refreshToken) {
	}

	public record AuthResponse(
			String accessToken,
			String refreshToken,
			String tokenType,
			long expiresIn,
			CurrentUserResponse user
	) {
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
