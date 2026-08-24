package com.gymos.identity.api;

import com.gymos.common.api.Correlation;
import com.gymos.identity.application.UserInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class UserInvitationController {
	private final UserInvitationService service;

	public UserInvitationController(UserInvitationService service) {
		this.service = service;
	}

	@PostMapping("/accept")
	InvitationAcceptedResponse accept(
			@Valid @RequestBody AcceptInvitationRequest request,
			HttpServletRequest servletRequest
	) {
		service.accept(request.token(), request.password(), correlationId(servletRequest));
		return new InvitationAcceptedResponse("ACTIVATED");
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record AcceptInvitationRequest(
			@NotBlank @Size(max = 512) String token,
			@NotBlank @Size(max = 120) String password
	) {
	}

	public record InvitationAcceptedResponse(String status) {
	}
}
