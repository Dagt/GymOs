package com.gymos.selfservice.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.MembershipController.ChangePlanRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipResponse;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.MembershipService;
import com.gymos.selfservice.api.SelfServiceController.CreateSessionResponse;
import com.gymos.selfservice.api.SelfServiceController.SelfServiceSummaryResponse;
import com.gymos.selfservice.api.SelfServiceController.VerifySessionRequest;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.ClientRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfServiceService {
	private static final int SESSION_MINUTES = 15;

	private final JdbcClient jdbcClient;
	private final SelfServiceRepository repository;
	private final MembershipService membershipService;
	private final AuditService auditService;

	public SelfServiceService(
			JdbcClient jdbcClient,
			SelfServiceRepository repository,
			MembershipService membershipService,
			AuditService auditService
	) {
		this.jdbcClient = jdbcClient;
		this.repository = repository;
		this.membershipService = membershipService;
		this.auditService = auditService;
	}

	@Transactional
	public CreateSessionResponse createSession(String terminalCode, String originIp, String correlationId) {
		TerminalRecord terminal = repository.terminal(terminalCode)
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_TERMINAL_NOT_FOUND", "El kiosco no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		UUID sessionId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO self_service_session (
				    id, organization_id, branch_id, terminal_id, status, started_at,
				    expires_at, origin_ip, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :terminalId, 'ACTIVE', :startedAt,
				    :expiresAt, :originIp, :createdAt, :updatedAt, 0
				)
				""")
				.param("id", sessionId)
				.param("organizationId", terminal.organizationId())
				.param("branchId", terminal.branchId())
				.param("terminalId", terminal.id())
				.param("startedAt", now)
				.param("expiresAt", now.plusMinutes(SESSION_MINUTES))
				.param("originIp", originIp)
				.param("createdAt", now)
				.param("updatedAt", now)
				.update();
		auditService.recordSystem(terminal.organizationId(), terminal.branchId(), null, "SELF_SERVICE_SESSION_STARTED", "SELF_SERVICE_SESSION", sessionId, "SELF_SERVICE_KIOSK", terminal.deviceCode(), correlationId);
		return new CreateSessionResponse(sessionId, terminal.name(), now.plusMinutes(SESSION_MINUTES), false);
	}

	@Transactional
	public SelfServiceSummaryResponse verify(UUID sessionId, VerifySessionRequest request, String correlationId) {
		SessionRecord session = activeSession(sessionId);
		ClientRecord client = repository.clientByIdentifier(session.organizationId(), request.identifier())
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_CLIENT_NOT_FOUND", "No encontramos un cliente activo con ese dato."));
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE self_service_session
				SET client_id = :clientId,
				    verification_method = 'CONTACT',
				    expires_at = :expiresAt,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("clientId", client.id())
				.param("expiresAt", now.plusMinutes(SESSION_MINUTES))
				.param("updatedAt", now)
				.param("sessionId", sessionId)
				.update();
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_CLIENT_VERIFIED", "CLIENT", client.id(), "SELF_SERVICE_KIOSK", null, correlationId);
		return summary(sessionId);
	}

	@Transactional(readOnly = true)
	public SelfServiceSummaryResponse summary(UUID sessionId) {
		SessionRecord session = verifiedSession(sessionId);
		ClientRecord client = repository.clientByIdentifier(session.organizationId(), clientIdentifier(session.clientId()))
				.orElse(null);
		List<MembershipRecord> memberships = repository.memberships(session.organizationId(), session.branchId(), session.clientId());
		List<PendingPaymentRecord> pendingPayments = repository.pendingPayments(session.organizationId(), session.branchId(), session.clientId());
		return new SelfServiceSummaryResponse(
				session.id(),
				session.expiresAt(),
				client == null ? "Cliente" : client.fullName(),
				memberships,
				pendingPayments
		);
	}

	@Transactional(readOnly = true)
	public List<PlanOptionRecord> membershipOptions(UUID sessionId) {
		SessionRecord session = verifiedSession(sessionId);
		return repository.planOptions(session.organizationId());
	}

	@Transactional
	public RenewMembershipResponse renew(UUID sessionId, UUID membershipId, RenewMembershipRequest request, String correlationId) {
		SessionRecord session = verifiedSession(sessionId);
		MembershipSaleResult response = membershipService.renew(kioskPrincipal(session), membershipId, new RenewMembershipCommand(
				request.method(),
				request.reference(),
				request.validateNow(),
				request.notes(),
				request.idempotencyKey()
		), correlationId);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_RENEWED", "MEMBERSHIP", membershipId, "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response);
	}

	@Transactional
	public RenewMembershipResponse changePlan(UUID sessionId, UUID membershipId, ChangePlanRequest request, String correlationId) {
		SessionRecord session = verifiedSession(sessionId);
		MembershipSaleResult response = membershipService.changePlan(kioskPrincipal(session), membershipId, new ChangeMembershipPlanCommand(
				request.targetPlanId(),
				request.method(),
				request.reference(),
				request.validateNow(),
				request.notes(),
				request.idempotencyKey()
		), correlationId);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_PLAN_CHANGED", "MEMBERSHIP", membershipId, "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response);
	}

	private RenewMembershipResponse toResponse(MembershipSaleResult result) {
		return new RenewMembershipResponse(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				result.membership()
		);
	}

	@Transactional
	public void end(UUID sessionId, String correlationId) {
		SessionRecord session = activeSession(sessionId);
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE self_service_session
				SET status = 'ENDED',
				    ended_at = :endedAt,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("endedAt", now)
				.param("updatedAt", now)
				.param("sessionId", sessionId)
				.update();
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_SESSION_ENDED", "SELF_SERVICE_SESSION", sessionId, "SELF_SERVICE_KIOSK", null, correlationId);
	}

	private SessionRecord verifiedSession(UUID sessionId) {
		SessionRecord session = activeSession(sessionId);
		if (session.clientId() == null) {
			throw new BusinessException("SELF_SERVICE_SESSION_NOT_VERIFIED", "Primero verifica tu identidad.");
		}
		return session;
	}

	private SessionRecord activeSession(UUID sessionId) {
		SessionRecord session = repository.session(sessionId)
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_SESSION_NOT_FOUND", "La sesion no existe."));
		if (!"ACTIVE".equals(session.status()) || session.expiresAt().isBefore(OffsetDateTime.now())) {
			throw new BusinessException("SELF_SERVICE_SESSION_EXPIRED", "La sesion del kiosco expiro.");
		}
		return session;
	}

	private GymOsPrincipal kioskPrincipal(SessionRecord session) {
		return new GymOsPrincipal(null, session.organizationId(), session.branchId(), "self-service-kiosk", "Kiosco", Set.of());
	}

	private String clientIdentifier(UUID clientId) {
		String value = jdbcClient.sql("""
				SELECT COALESCE(LOWER(p.email), LOWER(p.phone), '')
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.id = :clientId
				""")
				.param("clientId", clientId)
				.query(String.class)
				.single();
		return value;
	}
}
