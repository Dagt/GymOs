package com.gymos.access.api;

import com.gymos.access.application.AccessService;
import com.gymos.access.application.AccessService.PhysicalClientStateView;
import com.gymos.access.application.AccessService.PhysicalIntegrationView;
import com.gymos.access.application.AccessService.PhysicalDeviceLinkView;
import com.gymos.access.application.PhysicalConnectorService;
import com.gymos.access.application.PhysicalConnectorService.ActivationCodeView;
import com.gymos.access.infrastructure.PhysicalAccessRepository.PersonCandidateRecord;
import com.gymos.access.application.command.AccessRequestCommand;
import com.gymos.access.application.command.ConfigurePhysicalIntegrationCommand;
import com.gymos.access.application.command.ConsumeVisitPassCommand;
import com.gymos.access.application.command.CreateVisitPassCommand;
import com.gymos.access.application.command.LinkPhysicalClientCommand;
import com.gymos.access.application.command.ManualOpenCommand;
import com.gymos.access.application.command.TemporaryPassCommand;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.AccessLogRecord;
import com.gymos.access.infrastructure.AccessRepository.EffectiveAccessBenefitRecord;
import com.gymos.access.infrastructure.AccessRepository.TemporaryPassRecord;
import com.gymos.access.infrastructure.AccessRepository.VisitPassRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {
	private final AccessService accessService;
	private final PhysicalConnectorService connectorService;

	public AccessController(AccessService accessService, PhysicalConnectorService connectorService) {
		this.accessService = accessService;
		this.connectorService = connectorService;
	}

	@GetMapping("/devices")
	List<AccessDeviceRecord> devices(@AuthenticationPrincipal GymOsPrincipal principal) {
		return accessService.devices(principal);
	}

	@PostMapping("/devices")
	AccessDeviceRecord createDevice(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody AccessDeviceRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.createDevice(
				principal, request.name(), request.deviceCode(), request.deviceType(), request.provider(),
				request.requiredBenefitId(), correlationId(servletRequest));
	}

	@GetMapping("/devices/{deviceId}/benefits")
	List<UUID> deviceRequiredBenefits(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deviceId
	) {
		return accessService.deviceRequiredBenefits(principal, deviceId);
	}

	@GetMapping("/logs")
	List<AccessLogRecord> logs(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "30") int limit
	) {
		return accessService.logs(principal, limit);
	}

	@GetMapping("/visit-passes")
	List<VisitPassRecord> visitPasses(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) UUID clientId
	) {
		return accessService.visitPasses(principal, clientId);
	}

	@GetMapping("/clients/{clientId}/effective-benefits")
	List<EffectiveAccessBenefitRecord> effectiveBenefits(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		return accessService.effectiveBenefits(principal, clientId);
	}

	@GetMapping("/physical-integration")
	PhysicalIntegrationView physicalIntegration(@AuthenticationPrincipal GymOsPrincipal principal) {
		return accessService.physicalIntegration(principal);
	}

	@PutMapping("/physical-integration")
	PhysicalIntegrationView configurePhysicalIntegration(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody PhysicalIntegrationRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.configurePhysicalIntegration(
				principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/physical-integration/clients/{clientId}")
	PhysicalClientStateView physicalClientState(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		return accessService.physicalClientState(principal, clientId);
	}

	@PutMapping("/physical-integration/clients/{clientId}/link")
	PhysicalClientStateView linkPhysicalClient(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId,
			@Valid @RequestBody PhysicalClientLinkRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.linkPhysicalClient(
				principal, clientId, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/physical-integration/clients/{clientId}/synchronize")
	PhysicalClientStateView queuePhysicalSynchronization(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId,
			HttpServletRequest servletRequest
	) {
		return accessService.queuePhysicalSynchronization(principal, clientId, correlationId(servletRequest));
	}

	@PostMapping("/physical-integration/reconcile")
	ReconcileResult reconcileAll(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest servletRequest
	) {
		return new ReconcileResult(accessService.reconcileAllPhysicalClients(
				principal, correlationId(servletRequest)));
	}

	@PutMapping("/physical-integration/devices/{deviceId}/link")
	PhysicalDeviceLinkView linkPhysicalDevice(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deviceId,
			@Valid @RequestBody PhysicalDeviceLinkRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.linkPhysicalDevice(
				principal, deviceId, request.externalDeviceRef(), request.externalDeviceName(),
				request.authorizationMode(),
				correlationId(servletRequest));
	}

	@GetMapping("/physical-integration/device-candidates")
	java.util.List<com.gymos.access.infrastructure.PhysicalAccessRepository.DeviceCandidateRecord> physicalDeviceCandidates(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "100") int limit
	) {
		return accessService.physicalDeviceCandidates(principal, limit);
	}

	@DeleteMapping("/physical-integration/devices/{deviceId}/link")
	void unlinkPhysicalDevice(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deviceId,
			HttpServletRequest servletRequest
	) {
		accessService.unlinkPhysicalDevice(principal, deviceId, correlationId(servletRequest));
	}

	@PostMapping("/physical-integration/connector/activation")
	ActivationCodeView issueConnectorActivation(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody(required = false) ConnectorActivationIssueRequest request,
			HttpServletRequest servletRequest
	) {
		return connectorService.issueActivation(
				principal, request == null ? "ACTIVATION" : request.purpose(), correlationId(servletRequest));
	}

	@PostMapping("/physical-integration/connector/revoke")
	void revokeConnector(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest servletRequest
	) {
		connectorService.revoke(principal, correlationId(servletRequest));
	}

	@GetMapping("/physical-integration/candidates")
	List<PersonCandidateRecord> candidates(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "100") int limit
	) {
		return connectorService.candidates(principal, limit);
	}

	@PutMapping("/physical-integration/candidates/{candidateId}/match")
	PersonCandidateRecord matchCandidate(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID candidateId,
			@Valid @RequestBody CandidateMatchRequest request,
			HttpServletRequest servletRequest
	) {
		return connectorService.acceptCandidate(
				principal, candidateId, request.clientId(), correlationId(servletRequest));
	}

	@PostMapping("/physical-integration/candidates/{candidateId}/reject")
	PersonCandidateRecord rejectCandidate(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID candidateId,
			HttpServletRequest servletRequest
	) {
		return connectorService.rejectCandidate(principal, candidateId, correlationId(servletRequest));
	}

	@PutMapping("/devices/{deviceId}/benefits")
	List<UUID> replaceDeviceBenefits(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deviceId,
			@Valid @RequestBody DeviceBenefitsRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.replaceDeviceBenefits(
				principal, deviceId, request.benefitIds(), correlationId(servletRequest));
	}

	@PostMapping("/requests")
	AccessDecisionResponse requestAccess(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody AccessRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.requestAccess(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/commands/open")
	AccessDecisionResponse manualOpen(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody ManualOpenRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.manualOpen(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/commands/temporary")
	TemporaryPassRecord temporaryPass(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody TemporaryPassRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.createTemporaryPass(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/visit-passes")
	VisitPassRecord createVisitPass(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateVisitPassRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.createVisitPass(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/visit-passes/{visitPassId}/consume")
	VisitPassRecord consumeVisitPass(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID visitPassId,
			@Valid @RequestBody ConsumeVisitPassRequest request,
			HttpServletRequest servletRequest
	) {
		return accessService.consumeVisitPass(principal, visitPassId, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record AccessRequest(
			UUID clientId,
			@Size(max = 180) String identifier,
			@Size(max = 80) String deviceCode,
			@Size(max = 140) String idempotencyKey
	) {
		private AccessRequestCommand toCommand() {
			return new AccessRequestCommand(clientId, identifier, deviceCode, idempotencyKey);
		}
	}

	public record ManualOpenRequest(
			UUID clientId,
			@NotBlank @Size(max = 80) String deviceCode,
			@NotBlank @Size(max = 500) String reason
	) {
		private ManualOpenCommand toCommand() {
			return new ManualOpenCommand(clientId, deviceCode, reason);
		}
	}

	public record TemporaryPassRequest(
			@NotNull UUID clientId,
			OffsetDateTime startsAt,
			@NotNull OffsetDateTime expiresAt,
			@Min(1) int maxUses,
			@NotBlank @Size(max = 500) String reason
	) {
		private TemporaryPassCommand toCommand() {
			return new TemporaryPassCommand(clientId, startsAt, expiresAt, maxUses, reason);
		}
	}

	public record CreateVisitPassRequest(
			@NotNull UUID clientId,
			UUID saleId,
			@NotBlank @Pattern(regexp = "DROP_IN|VISIT_PACK|COURTESY|PROMOTION") String passType,
			@Min(1) int totalVisits,
			LocalDate validFrom,
			LocalDate validUntil
	) {
		private CreateVisitPassCommand toCommand() {
			return new CreateVisitPassCommand(clientId, saleId, passType, totalVisits, validFrom, validUntil);
		}
	}

	public record ConsumeVisitPassRequest(@Size(max = 500) String reason) {
		private ConsumeVisitPassCommand toCommand() {
			return new ConsumeVisitPassCommand(reason);
		}
	}

	public record DeviceBenefitsRequest(
			@Size(max = 100) List<@NotNull UUID> benefitIds
	) {
	}

	public record AccessDeviceRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 80) String deviceCode,
			@NotBlank @Pattern(regexp = "TURNSTILE|DOOR|GATE|ZONE|BIOMETRIC") String deviceType,
			@Size(max = 60) String provider,
			UUID requiredBenefitId
	) {
	}

	public record PhysicalIntegrationRequest(
			boolean enabled,
			@NotBlank @Size(max = 60) String providerKey,
			@NotBlank @Size(max = 160) String displayName,
			@Size(max = 120) String connectorKey,
			@Size(max = 100) List<@Valid PhysicalBenefitMappingRequest> benefitMappings
	) {
		private ConfigurePhysicalIntegrationCommand toCommand() {
			return new ConfigurePhysicalIntegrationCommand(
					enabled, providerKey, displayName, connectorKey,
					benefitMappings == null ? List.of() : benefitMappings.stream()
							.map(PhysicalBenefitMappingRequest::toCommand)
							.toList());
		}
	}

	public record PhysicalBenefitMappingRequest(
			@NotNull UUID benefitId,
			@NotBlank @Size(max = 180) String externalAccessLevelRef,
			@Size(max = 180) String externalAccessLevelName
	) {
		private ConfigurePhysicalIntegrationCommand.BenefitMapping toCommand() {
			return new ConfigurePhysicalIntegrationCommand.BenefitMapping(
					benefitId, externalAccessLevelRef, externalAccessLevelName);
		}
	}

	public record PhysicalClientLinkRequest(@NotBlank @Size(max = 180) String externalPersonRef) {
		private LinkPhysicalClientCommand toCommand() {
			return new LinkPhysicalClientCommand(externalPersonRef);
		}
	}

	public record PhysicalDeviceLinkRequest(
			@NotBlank @Size(max = 180) String externalDeviceRef,
			@Size(max = 180) String externalDeviceName,
			@Pattern(regexp = "PROVIDER_MANAGED|GYMFORT_ONLINE") String authorizationMode
	) {
	}

	public record ConnectorActivationIssueRequest(
			@Pattern(regexp = "ACTIVATION|ROTATION") String purpose
	) {
	}

	public record CandidateMatchRequest(@NotNull UUID clientId) {
	}

	public record ReconcileResult(int queuedClients) {
	}
}
