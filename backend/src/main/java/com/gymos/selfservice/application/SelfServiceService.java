package com.gymos.selfservice.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.InMemoryRateLimiter;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.MembershipService;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSaleResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSummaryResult;
import com.gymos.selfservice.application.result.SelfServiceSummaryResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.ClientRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfServiceService {
	private static final int SESSION_MINUTES = 15;
	private static final int MAX_VERIFICATION_ATTEMPTS = 5;
	private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final JdbcClient jdbcClient;
	private final SelfServiceRepository repository;
	private final MembershipService membershipService;
	private final AuditService auditService;
	private final InMemoryRateLimiter rateLimiter;

	public SelfServiceService(
			JdbcClient jdbcClient,
			SelfServiceRepository repository,
			MembershipService membershipService,
			AuditService auditService,
			InMemoryRateLimiter rateLimiter
	) {
		this.jdbcClient = jdbcClient;
		this.repository = repository;
		this.membershipService = membershipService;
		this.auditService = auditService;
		this.rateLimiter = rateLimiter;
	}

	@Transactional
	public CreateSessionResult createSession(String terminalCode, String originIp, String correlationId) {
		rateLimiter.check("kiosk:create:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:create:terminal:" + terminalCode, 20, Duration.ofMinutes(1));
		TerminalRecord terminal = repository.terminal(terminalCode)
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_TERMINAL_NOT_FOUND", "El kiosco no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		UUID sessionId = UUID.randomUUID();
		String sessionToken = randomToken();
		jdbcClient.sql("""
				INSERT INTO self_service_session (
				    id, organization_id, branch_id, terminal_id, status, started_at,
				    expires_at, origin_ip, session_token_hash, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :terminalId, 'ACTIVE', :startedAt,
				    :expiresAt, :originIp, :sessionTokenHash, :createdAt, :updatedAt, 0
				)
				""")
				.param("id", sessionId)
				.param("organizationId", terminal.organizationId())
				.param("branchId", terminal.branchId())
				.param("terminalId", terminal.id())
				.param("startedAt", now)
				.param("expiresAt", now.plusMinutes(SESSION_MINUTES))
				.param("originIp", originIp)
				.param("sessionTokenHash", tokenHash(sessionToken))
				.param("createdAt", now)
				.param("updatedAt", now)
				.update();
		auditService.recordSystem(terminal.organizationId(), terminal.branchId(), null, "SELF_SERVICE_SESSION_STARTED", "SELF_SERVICE_SESSION", sessionId, "SELF_SERVICE_KIOSK", terminal.deviceCode(), correlationId);
		return new CreateSessionResult(sessionId, sessionToken, terminal.name(), now.plusMinutes(SESSION_MINUTES), false);
	}

	@Transactional
	public SelfServiceSummaryResult verify(UUID sessionId, String sessionToken, VerifySessionCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:verify:ip:" + originIp, 60, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:verify:session:" + sessionId, 10, Duration.ofMinutes(1));
		SessionRecord session = activeSession(sessionId, sessionToken);
		ensureNotBlocked(session);
		ClientRecord client = repository.clientByIdentifier(session.organizationId(), request.identifier())
				.orElse(null);
		if (client == null) {
			recordFailedVerification(session, correlationId);
			throw new BusinessException("SELF_SERVICE_VERIFICATION_FAILED", "No fue posible verificar la sesion con los datos proporcionados.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE self_service_session
				SET client_id = :clientId,
				    verification_method = 'CONTACT',
				    verification_attempts = 0,
				    blocked_until = NULL,
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
		return summary(sessionId, sessionToken);
	}

	@Transactional(readOnly = true)
	public SelfServiceSummaryResult summary(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		ClientRecord client = repository.clientByIdentifier(session.organizationId(), clientIdentifier(session.clientId()))
				.orElse(null);
		List<MembershipRecord> memberships = repository.memberships(session.organizationId(), session.branchId(), session.clientId());
		List<PendingPaymentRecord> pendingPayments = repository.pendingPayments(session.organizationId(), session.branchId(), session.clientId());
		return new SelfServiceSummaryResult(
				session.id(),
				session.expiresAt(),
				client == null ? "Cliente" : client.fullName(),
				memberships,
				pendingPayments
		);
	}

	@Transactional(readOnly = true)
	public List<PlanOptionRecord> membershipOptions(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		return repository.planOptions(session.organizationId());
	}

	@Transactional
	public SelfServiceMembershipSaleResult renew(UUID sessionId, String sessionToken, UUID membershipId, RenewMembershipCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:renew:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:renew:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		MembershipSaleResult response = membershipService.renew(kioskPrincipal(session), membershipId, request, correlationId);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_RENEWED", "MEMBERSHIP", membershipId, "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response);
	}

	@Transactional
	public SelfServiceMembershipSaleResult changePlan(UUID sessionId, String sessionToken, UUID membershipId, ChangeMembershipPlanCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:change-plan:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:change-plan:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		MembershipSaleResult response = membershipService.changePlan(kioskPrincipal(session), membershipId, request, correlationId);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_PLAN_CHANGED", "MEMBERSHIP", membershipId, "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response);
	}

	private SelfServiceMembershipSaleResult toResponse(MembershipSaleResult result) {
		return new SelfServiceMembershipSaleResult(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				result.membership() == null ? null : new SelfServiceMembershipSummaryResult(
						result.membership().id(),
						result.membership().clientId(),
						result.membership().planId(),
						result.membership().planName(),
						result.membership().saleId(),
						result.membership().saleNumber(),
						result.membership().status(),
						result.membership().activatedAt(),
						result.membership().startsOn(),
						result.membership().endsOn(),
						result.membership().graceEndsOn()
				)
		);
	}

	@Transactional
	public void end(UUID sessionId, String sessionToken, String correlationId) {
		SessionRecord session = activeSession(sessionId, sessionToken);
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

	private SessionRecord verifiedSession(UUID sessionId, String sessionToken) {
		SessionRecord session = activeSession(sessionId, sessionToken);
		if (session.clientId() == null) {
			throw new BusinessException("SELF_SERVICE_SESSION_NOT_VERIFIED", "Primero verifica tu identidad.");
		}
		return session;
	}

	private SessionRecord activeSession(UUID sessionId, String sessionToken) {
		SessionRecord session = repository.session(sessionId)
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_SESSION_NOT_FOUND", "La sesion no existe."));
		if (session.sessionTokenHash() == null || sessionToken == null || !MessageDigest.isEqual(session.sessionTokenHash().getBytes(StandardCharsets.UTF_8), tokenHash(sessionToken).getBytes(StandardCharsets.UTF_8))) {
			throw new BusinessException("SELF_SERVICE_SESSION_INVALID", "La sesion del kiosco no es valida.");
		}
		if (!"ACTIVE".equals(session.status()) || session.expiresAt().isBefore(OffsetDateTime.now())) {
			throw new BusinessException("SELF_SERVICE_SESSION_EXPIRED", "La sesion del kiosco expiro.");
		}
		return session;
	}

	private void ensureNotBlocked(SessionRecord session) {
		if (session.blockedUntil() != null && session.blockedUntil().isAfter(OffsetDateTime.now())) {
			throw new BusinessException("SELF_SERVICE_SESSION_BLOCKED", "Demasiados intentos. Espera un momento y vuelve a intentar.");
		}
	}

	private void recordFailedVerification(SessionRecord session, String correlationId) {
		OffsetDateTime now = OffsetDateTime.now();
		int attempts = session.verificationAttempts() + 1;
		OffsetDateTime blockedUntil = attempts >= MAX_VERIFICATION_ATTEMPTS ? now.plus(BLOCK_DURATION) : null;
		jdbcClient.sql("""
				UPDATE self_service_session
				SET verification_attempts = :verificationAttempts,
				    blocked_until = :blockedUntil,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("verificationAttempts", attempts)
				.param("blockedUntil", blockedUntil)
				.param("updatedAt", now)
				.param("sessionId", session.id())
				.update();
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_VERIFICATION_FAILED", "SELF_SERVICE_SESSION", session.id(), "SELF_SERVICE_KIOSK", null, correlationId);
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

	private String randomToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String tokenHash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}
}
