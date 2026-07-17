package com.gymos.identity.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.application.UserAdministrationService;
import com.gymos.identity.application.command.CreateInternalUserCommand;
import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdministrationController {
	private final UserAdministrationService service;

	public UserAdministrationController(UserAdministrationService service) {
		this.service = service;
	}

	@GetMapping("/roles")
	List<RoleOption> roles(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.roles(principal);
	}

	@GetMapping("/internal")
	List<InternalUserSummary> users(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.users(principal);
	}

	@PostMapping("/internal")
	InternalUserSummary createUser(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateInternalUserRequest request,
			HttpServletRequest servletRequest
	) {
		return service.createUser(principal, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateInternalUserRequest(
			@NotBlank @Size(max = 120) String firstName,
			@NotBlank @Size(max = 120) String lastName,
			@Email @Size(max = 180) String email,
			@Size(max = 32) String phone,
			@NotBlank @Size(max = 120) String username,
			@NotBlank @Size(min = 8, max = 120) String temporaryPassword,
			@NotNull UUID roleId,
			List<UUID> branchIds
	) {
		private CreateInternalUserCommand toCommand() {
			return new CreateInternalUserCommand(firstName, lastName, email, phone, username, temporaryPassword, roleId, branchIds);
		}
	}
}
