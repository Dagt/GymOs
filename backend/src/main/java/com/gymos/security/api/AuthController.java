package com.gymos.security.api;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.security.application.AuthService;
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
	AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.username(), request.password());
	}

	@PostMapping("/refresh")
	AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	void logout(@AuthenticationPrincipal GymOsPrincipal principal, @Valid @RequestBody LogoutRequest request) {
		authService.logout(principal, request.refreshToken());
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal GymOsPrincipal principal) {
		return authService.currentUser(principal);
	}

	@PostMapping("/select-branch")
	AuthResponse selectBranch(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectBranchRequest request
	) {
		return authService.selectBranch(principal, request.branchId());
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record LogoutRequest(@NotBlank String refreshToken) {
	}

	public record SelectBranchRequest(@NotNull UUID branchId) {
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
			UUID organizationId,
			UUID branchId,
			String username,
			String fullName,
			Set<String> permissions
	) {
	}
}
