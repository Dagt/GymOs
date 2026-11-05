
package com.gymos.access.api;

import com.gymos.access.application.ManualCheckInService;
import com.gymos.access.application.ManualCheckInService.History;
import com.gymos.access.application.ManualCheckInService.Preview;
import com.gymos.access.infrastructure.ManualCheckInRepository.Client;
import com.gymos.access.infrastructure.ManualCheckInRepository.Entry;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access/manual-check-ins")
public class ManualCheckInController {
	private final ManualCheckInService service;

	public ManualCheckInController(ManualCheckInService service) {
		this.service = service;
	}

	@Operation(summary = "Buscar socios visibles en la sucursal activa")
	@GetMapping("/clients")
	List<Client> search(@AuthenticationPrincipal GymOsPrincipal actor, @RequestParam String query) {
		return service.search(actor, query);
	}

	@Operation(summary = "Consultar derechos sin registrar una entrada")
	@GetMapping("/clients/{clientId}")
	Preview preview(@AuthenticationPrincipal GymOsPrincipal actor, @PathVariable UUID clientId) {
		return service.preview(actor, clientId);
	}

	@Operation(summary = "Consultar las últimas 30 entradas manuales de la sucursal activa")
	@GetMapping
	History history(@AuthenticationPrincipal GymOsPrincipal actor) {
		return service.history(actor);
	}

	@Operation(summary = "Registrar una entrada presencial", description = "Reintentar con la misma clave y el mismo cuerpo.")
	@PostMapping
	Entry register(@AuthenticationPrincipal GymOsPrincipal actor, @RequestHeader("Idempotency-Key") UUID key,
			@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		return service.register(actor, body.branchId(), key, body.clientId(), body.benefitId(),
				body.overrideOf(), body.overrideReason(), (String) request.getAttribute(Correlation.ATTRIBUTE));
	}

	@Operation(summary = "Revertir una entrada autorizada y devolver la visita cuando corresponda")
	@PostMapping("/{id}/reversal")
	Entry reverse(@AuthenticationPrincipal GymOsPrincipal actor, @PathVariable UUID id,
			@Valid @RequestBody ReverseRequest body, HttpServletRequest request) {
		return service.reverse(actor, body.branchId(), id, body.reason(), (String) request.getAttribute(Correlation.ATTRIBUTE));
	}

	record RegisterRequest(@NotNull UUID branchId, @NotNull UUID clientId, UUID benefitId,
			UUID overrideOf, @Size(max = 300) String overrideReason) {}
	record ReverseRequest(@NotNull UUID branchId, @NotBlank @Size(max = 300) String reason) {}
}
