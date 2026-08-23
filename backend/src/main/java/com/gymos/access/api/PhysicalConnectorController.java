package com.gymos.access.api;

import com.gymos.access.application.PhysicalConnectorService;
import com.gymos.access.application.PhysicalConnectorService.ConnectorActivationView;
import com.gymos.access.application.PhysicalConnectorService.ConnectorHealthView;
import com.gymos.access.application.PhysicalConnectorService.ConnectorJobView;
import com.gymos.access.application.PhysicalConnectorService.PersonCandidateInput;
import com.gymos.access.application.PhysicalConnectorService.PhysicalEventInput;
import com.gymos.access.application.PhysicalConnectorService.PhysicalEventView;
import com.gymos.access.infrastructure.PhysicalAccessRepository.PersonCandidateRecord;
import com.gymos.common.api.Correlation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/physical-connectors")
public class PhysicalConnectorController {
	public static final String CONNECTOR_KEY_HEADER = "X-GymFort-Connector-Key";
	public static final String CONNECTOR_SECRET_HEADER = "X-GymFort-Connector-Secret";

	private final PhysicalConnectorService connectorService;

	public PhysicalConnectorController(PhysicalConnectorService connectorService) {
		this.connectorService = connectorService;
	}

	@PostMapping("/activate")
	ConnectorActivationView activate(
			@Valid @RequestBody ConnectorActivationRequest request,
			HttpServletRequest servletRequest
	) {
		return connectorService.activate(
				request.connectorKey(), request.activationCode(), correlationId(servletRequest));
	}

	@PostMapping("/heartbeat")
	ConnectorHealthView heartbeat(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@Valid @RequestBody ConnectorHeartbeatRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.heartbeat(
				connector, request.status(), request.connectorVersion(), request.instanceId(), request.lastError());
	}

	@PostMapping("/jobs/claim")
	List<ConnectorJobView> claimJobs(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@Valid @RequestBody(required = false) ConnectorClaimRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		ConnectorClaimRequest safeRequest = request == null ? new ConnectorClaimRequest(20, 90) : request;
		return connectorService.claimJobs(connector, safeRequest.limit(), safeRequest.leaseSeconds());
	}

	@PostMapping("/jobs/{jobId}/complete")
	ConnectorJobView completeJob(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@PathVariable UUID jobId,
			@Valid @RequestBody ConnectorCompleteRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.completeJob(
				connector, jobId, request.claimToken(), request.externalOperationRef());
	}

	@PostMapping("/jobs/{jobId}/fail")
	ConnectorJobView failJob(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@PathVariable UUID jobId,
			@Valid @RequestBody ConnectorFailRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.failJob(
				connector, jobId, request.claimToken(), request.error(),
				request.retryable() == null || request.retryable(),
				request.retryAfterSeconds() == null ? 30 : request.retryAfterSeconds());
	}

	@PostMapping("/jobs/{jobId}/lease")
	ConnectorJobView extendLease(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@PathVariable UUID jobId,
			@Valid @RequestBody ConnectorLeaseRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.extendLease(
				connector, jobId, request.claimToken(), request.leaseSeconds());
	}

	@PostMapping("/events")
	PhysicalEventView event(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@Valid @RequestBody ConnectorPhysicalEventRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.ingestEvent(connector, request.toInput(), correlationId);
	}

	@PostMapping("/person-candidates")
	PersonCandidateRecord personCandidate(
			@RequestHeader(name = CONNECTOR_KEY_HEADER, required = false) String connectorKey,
			@RequestHeader(name = CONNECTOR_SECRET_HEADER, required = false) String connectorSecret,
			@Valid @RequestBody ConnectorPersonCandidateRequest request,
			HttpServletRequest servletRequest
	) {
		String correlationId = correlationId(servletRequest);
		var connector = connectorService.authenticate(connectorKey, connectorSecret, correlationId);
		return connectorService.ingestPersonCandidate(connector, request.toInput());
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? UUID.randomUUID().toString() : value.toString();
	}

	public record ConnectorActivationRequest(
			@NotBlank @Size(max = 120) String connectorKey,
			@NotBlank @Size(max = 200) String activationCode
	) {
	}

	public record ConnectorHeartbeatRequest(
			@Pattern(regexp = "HEALTHY|DEGRADED") String status,
			@Size(max = 80) String connectorVersion,
			@Size(max = 120) String instanceId,
			@Size(max = 1000) String lastError
	) {
	}

	public record ConnectorClaimRequest(
			@Min(1) @Max(50) int limit,
			@Min(30) @Max(300) int leaseSeconds
	) {
	}

	public record ConnectorCompleteRequest(
			@NotNull UUID claimToken,
			@Size(max = 180) String externalOperationRef
	) {
	}

	public record ConnectorFailRequest(
			@NotNull UUID claimToken,
			@NotBlank @Size(max = 1000) String error,
			Boolean retryable,
			@Min(0) @Max(3600) Integer retryAfterSeconds
	) {
	}

	public record ConnectorLeaseRequest(
			@NotNull UUID claimToken,
			@Min(30) @Max(300) int leaseSeconds
	) {
	}

	public record ConnectorPhysicalEventRequest(
			@NotBlank @Size(max = 60) String providerKey,
			@NotBlank @Size(max = 180) String externalEventId,
			@Size(max = 180) String externalPersonRef,
			@NotBlank @Size(max = 180) String externalDeviceRef,
			OffsetDateTime occurredAt,
			@NotBlank @Pattern(regexp = "GRANTED|DENIED") String decision,
			@NotBlank @Size(max = 80) String eventType,
			@Size(max = 80) String reasonCode,
			@Size(max = 20) Map<@Size(max = 60) String, @Size(max = 240) String> metadata
	) {
		private PhysicalEventInput toInput() {
			return new PhysicalEventInput(
					providerKey, externalEventId, externalPersonRef, externalDeviceRef,
					occurredAt, decision, eventType, reasonCode, metadata);
		}
	}

	public record ConnectorPersonCandidateRequest(
			@NotBlank @Size(max = 180) String externalPersonRef,
			@Size(max = 180) String externalDisplayName,
			@Size(max = 254) String email,
			@Size(max = 40) String phone,
			@Size(max = 120) String externalEmployeeNumber
	) {
		private PersonCandidateInput toInput() {
			return new PersonCandidateInput(
					externalPersonRef, externalDisplayName, email, phone, externalEmployeeNumber);
		}
	}
}
