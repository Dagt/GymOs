package com.gymos.visitpackages.api;

import com.gymos.clients.api.operations.ClientBranchAccess;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.visitpackages.api.operations.VisitPackageOperations;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.PurchaseVisitPackageCommand;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackagePurchaseResult;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageQuoteView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VisitPackageController {
	private final VisitPackageOperations operations;
	private final ClientBranchAccess clients;

	public VisitPackageController(VisitPackageOperations operations, ClientBranchAccess clients) {
		this.operations = operations;
		this.clients = clients;
	}

	@PostMapping("/visit-packages/quotes")
	VisitPackageQuoteView quote(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody VisitPackageQuoteRequest request
	) {
		clients.requireVisible(principal, request.clientId());
		return operations.quote(principal, request.clientId(), request.packagePlanId());
	}

	@PostMapping("/visit-packages/purchases")
	VisitPackagePurchaseResult purchase(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody VisitPackagePurchaseRequest request,
			HttpServletRequest servletRequest
	) {
		clients.requireVisible(principal, request.clientId());
		return operations.purchase(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/clients/{clientId}/visit-package")
	VisitPackageEntitlementView current(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		clients.requireVisible(principal, clientId);
		return operations.current(principal, clientId).orElse(null);
	}

	@GetMapping("/clients/{clientId}/visit-packages")
	List<VisitPackageEntitlementView> history(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		clients.requireVisible(principal, clientId);
		return operations.history(principal, clientId, 20);
	}

	public record VisitPackageQuoteRequest(@NotNull UUID clientId, @NotNull UUID packagePlanId) {}

	public record VisitPackagePurchaseRequest(
			@NotNull UUID clientId,
			@NotNull UUID packagePlanId,
			@NotBlank @Size(max = 32)
			@Pattern(regexp = "(?i)\\s*(CASH|TRANSFER|CARD_MANUAL|CARD_MERCADO_PAGO)\\s*",
					message = "Selecciona un método de pago de recepción válido") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 1000) String notes,
			@NotBlank @Size(max = 120) String idempotencyKey
	) {
		PurchaseVisitPackageCommand toCommand() {
			return new PurchaseVisitPackageCommand(
					clientId, packagePlanId, method, reference, validateNow, notes, idempotencyKey);
		}
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}
}
