package com.gymos.clients.api;

import com.gymos.clients.application.ClientService;
import com.gymos.clients.application.command.CreateClientCommand;
import com.gymos.clients.application.command.UpdateClientCommand;
import com.gymos.clients.application.result.ClientDetailResult;
import com.gymos.clients.infrastructure.ClientRepository.ClientDetail;
import com.gymos.clients.infrastructure.ClientRepository.ClientSummary;
import com.gymos.clients.infrastructure.ClientRepository.ClientTimelineEntry;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {
	private final ClientService clientService;

	public ClientController(ClientService clientService) {
		this.clientService = clientService;
	}

	@GetMapping
	List<ClientSummary> search(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String status
	) {
		return clientService.search(principal, query, status);
	}

	@PostMapping
	ClientDetailResponse create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ClientCreateRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(clientService.create(principal, request.toCommand(), correlationId(servletRequest)));
	}

	@GetMapping("/{clientId}")
	ClientDetailResponse detail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID clientId) {
		return toResponse(clientService.detail(principal, clientId));
	}

	@PutMapping("/{clientId}")
	ClientDetailResponse update(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId,
			@Valid @RequestBody ClientUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(clientService.update(principal, clientId, request.toCommand(), correlationId(servletRequest)));
	}

	private ClientDetailResponse toResponse(ClientDetailResult result) {
		return new ClientDetailResponse(result.client(), result.timeline());
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record ClientCreateRequest(
			@NotBlank @Size(max = 120) String firstName,
			@NotBlank @Size(max = 120) String lastName,
			LocalDate birthDate,
			@Size(max = 32) String gender,
			@Size(max = 32) String phone,
			@Email @Size(max = 180) String email,
			@Size(max = 512) String photoReference,
			@Size(max = 2000) String notes
	) {
		private CreateClientCommand toCommand() {
			return new CreateClientCommand(firstName, lastName, birthDate, gender, phone, email, photoReference, notes);
		}
	}

	public record ClientUpdateRequest(
			@NotBlank @Size(max = 120) String firstName,
			@NotBlank @Size(max = 120) String lastName,
			LocalDate birthDate,
			@Size(max = 32) String gender,
			@Size(max = 32) String phone,
			@Email @Size(max = 180) String email,
			@Size(max = 512) String photoReference,
			@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED") String status,
			@Size(max = 2000) String notes
	) {
		private UpdateClientCommand toCommand() {
			return new UpdateClientCommand(firstName, lastName, birthDate, gender, phone, email, photoReference, status, notes);
		}
	}

	public record ClientDetailResponse(ClientDetail client, List<ClientTimelineEntry> timeline) {
	}
}
