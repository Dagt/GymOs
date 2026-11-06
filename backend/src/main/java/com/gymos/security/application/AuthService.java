package com.gymos.security.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.api.ApiAuthenticationException;
import com.gymos.common.api.ApiConflictException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.common.security.SecurityProperties;
import com.gymos.common.security.RefreshCredentials;
import com.gymos.common.security.RefreshCredentials.Credential;
import com.gymos.common.security.SessionBinding;
import com.gymos.common.security.SessionContext;
import com.gymos.common.security.SessionRegistry;
import com.gymos.common.security.SessionRegistry.Rotation;
import com.gymos.common.security.SessionRegistry.State;
import com.gymos.common.security.UserAuthorities;
import com.gymos.security.application.result.AuthResult;
import com.gymos.security.application.result.CurrentUserResult;
import com.gymos.security.infrastructure.AuthQueryRepository;
import com.gymos.security.infrastructure.AuthQueryRepository.AuthenticatedUser;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private final AuthenticationManager authenticationManager;
	private final AuthQueryRepository authQueryRepository;
	private final JwtService jwtService;
	private final SessionRegistry sessions;
	private final RefreshCredentials refreshCredentials;
	private final AuditService auditService;
	private final SecurityProperties securityProperties;

	public AuthService(AuthenticationManager authenticationManager, AuthQueryRepository authQueryRepository,
			JwtService jwtService, SessionRegistry sessions, RefreshCredentials refreshCredentials,
			AuditService auditService, SecurityProperties securityProperties) {
		this.authenticationManager = authenticationManager;
		this.authQueryRepository = authQueryRepository;
		this.jwtService = jwtService;
		this.sessions = sessions;
		this.refreshCredentials = refreshCredentials;
		this.auditService = auditService;
		this.securityProperties = securityProperties;
	}

	@Transactional
	public AuthResult login(String username, String password, String previousCookie, String correlationId) {
		// Bind authentication to the password generation observed before checking it.
		// A concurrent recovery must not turn an old-password login into a new-generation session.
		var beforeAuthentication = authQueryRepository.findActiveUserByLoginIdentifier(username);
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		AuthenticatedUser user = authQueryRepository.findActiveUserByLoginIdentifier(username)
				.orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Usuario o password invalidos."));
		if (beforeAuthentication.filter(before -> before.userId().equals(user.userId())
				&& before.credentialVersion() == user.credentialVersion()).isEmpty()) {
			throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
		}
		if (user.accountScope() == AccountScope.TENANT
				&& !authQueryRepository.organizationIsActive(user.organizationId())) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		if (user.accountScope() == AccountScope.PLATFORM && !user.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "La cuenta de plataforma no tiene un rol administrativo valido.");
		}
		GymOsPrincipal principal = user.toPrincipal(defaultBranchFor(user));
		String fingerprint = fingerprint(user);
		Instant expiresAt = Instant.now().plus(Duration.ofDays(securityProperties.refreshTokenDays()));
		SessionContext context = SessionContext.from(principal, fingerprint, expiresAt);
		UUID sessionId = sessions.create(context.serialize(), expiresAt);
		refreshCredentials.parse(previousCookie)
				.ifPresent(previous -> sessions.revokeFamily(previous.sessionId(), previous.generation()));
		AuthResult response = issueAccess(principal, context, sessionId, 0, 0);
		auditService.recordSystem(
				principal.organizationId(), null, principal.userId(), "AUTH_LOGIN", "USER",
				principal.userId(), "BACKOFFICE", null, correlationId);
		return response;
	}

	@Transactional(noRollbackFor = ApiAuthenticationException.class)
	public AuthResult refresh(String refreshToken, UUID operationId, String correlationId) {
		Credential credential = refreshCredentials.parse(refreshToken).orElseThrow(this::invalidRefresh);
		UUID id = credential.sessionId();
		State state = sessions.find(id).orElseThrow(this::invalidRefresh);
		SessionContext context = parseSessionContext(state.context());
		GymOsPrincipal principal = restoreOrRevoke(context, id, state.revision());
		Rotation rotation = sessions.rotate(id, credential.generation(), state.revision(), operationId.toString(),
				state.revision(), state.context());
		if (rotation == Rotation.REUSED) {
			auditService.recordSystem(context.organizationId(), context.branchId(), context.userId(),
					"AUTH_REFRESH_REUSED", "USER", context.userId(), "BACKOFFICE", null, correlationId);
			throw invalidRefresh();
		}
		long generation = rotatedGeneration(rotation, credential.generation());
		State current = sessions.find(id).filter(value -> value.generation() == generation)
				.orElseThrow(AuthService::sessionConflict);
		if (current.revision() != state.revision()) throw sessionConflict();
		return issueAccess(principal, context, id, generation, state.revision());
	}

	@Transactional
	public void logout(String refreshToken, String correlationId) {
		refreshCredentials.parse(refreshToken).ifPresent(credential -> sessions.find(credential.sessionId())
				.ifPresent(state -> {
					if (sessions.revokeFamily(credential.sessionId(), credential.generation())) {
						SessionContext context = parseSessionContext(state.context());
						auditService.recordSystem(context.organizationId(), context.branchId(), context.userId(),
								"AUTH_LOGOUT", "USER", context.userId(), "BACKOFFICE", null, correlationId);
					}
				}));
	}

	@Transactional
	public AuthResult selectBranch(GymOsPrincipal principal, UUID branchId, String refreshToken, String correlationId) {
		requirePrincipal(principal);
		AuthenticatedUser user = activeUser(principal.userId());
		GymOsPrincipal selectedPrincipal;
		if (principal.credentialVersion() != user.credentialVersion()) throw invalidRefresh();
		if (principal.tenantIdentity()) {
			if (user.accountScope() != AccountScope.TENANT
					|| !user.organizationId().equals(principal.organizationId())
					|| !authQueryRepository.branchBelongsToOrganization(branchId, user.organizationId())
					|| !authQueryRepository.userHasBranchAccess(user.userId(), branchId)) {
				throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
			}
			selectedPrincipal = user.toPrincipal(branchId);
		} else {
			requireActiveSupport(principal);
			if (!user.platformAdmin()
					|| !authQueryRepository.branchBelongsToOrganization(branchId, principal.organizationId())) {
				throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
			}
			selectedPrincipal = user.toSupportPrincipal(
					principal.organizationId(), branchId, principal.supportExpiresAt());
		}
		AuthResult response = changeContext(principal, selectedPrincipal, user, refreshToken);
		auditService.recordSystem(
				selectedPrincipal.organizationId(), branchId, principal.userId(), "BRANCH_SELECTED", "BRANCH",
				branchId, principal.supportMode() ? "PLATFORM_SUPPORT" : "BACKOFFICE", null, correlationId);
		return response;
	}

	@Transactional
	public AuthResult selectOrganization(
			GymOsPrincipal principal,
			UUID organizationId,
			String reason,
			String refreshToken,
			String correlationId
	) {
		requirePlatformAdmin(principal);
		String normalizedReason = normalizeSupportReason(reason);
		if (!authQueryRepository.organizationIsActive(organizationId)) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		AuthenticatedUser user = activeUser(principal.userId());
		if (!user.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "La cuenta ya no tiene acceso administrativo de plataforma.");
		}
		UUID branchId = authQueryRepository.firstActiveBranch(organizationId).orElse(null);
		Instant expiresAt = Instant.now().plus(Duration.ofMinutes(securityProperties.platformSupportMinutes()))
				.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		GymOsPrincipal supportPrincipal = user.toSupportPrincipal(organizationId, branchId, expiresAt);
		AuthResult response = changeContext(principal, supportPrincipal, user, refreshToken);
		auditService.recordSystem(
				organizationId, branchId, principal.userId(), "PLATFORM_SUPPORT_STARTED", "ORGANIZATION",
				organizationId, "PLATFORM_SUPPORT", normalizedReason, correlationId);
		return response;
	}

	@Transactional
	public AuthResult exitSupport(
			GymOsPrincipal principal,
			String refreshToken,
			String correlationId
	) {
		requireActiveSupport(principal);
		AuthenticatedUser user = activeUser(principal.userId());
		if (!user.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "La cuenta ya no tiene acceso administrativo de plataforma.");
		}
		GymOsPrincipal platformPrincipal = user.toPrincipal(null);
		AuthResult response = changeContext(principal, platformPrincipal, user, refreshToken);
		auditService.recordSystem(
				principal.organizationId(), principal.branchId(), principal.userId(), "PLATFORM_SUPPORT_ENDED",
				"ORGANIZATION", principal.organizationId(), "PLATFORM_SUPPORT", null, correlationId);
		return response;
	}

	public CurrentUserResult currentUser(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return toResponse(principal);
	}

	private AuthResult changeContext(GymOsPrincipal principal, GymOsPrincipal next, AuthenticatedUser user,
			String refreshToken) {
		SessionBinding binding = principal.session();
		Credential credential = refreshCredentials.parse(refreshToken).orElseThrow(this::invalidRefresh);
		if (binding == null || !binding.id().equals(credential.sessionId())
				|| principal.credentialVersion() != user.credentialVersion()
				|| !binding.authorityFingerprint().equals(fingerprint(user))) throw invalidRefresh();
		State state = sessions.find(binding.id()).orElseThrow(this::invalidRefresh);
		if (state.revision() != binding.revision() || state.generation() != credential.generation()) throw sessionConflict();
		SessionContext previous = parseSessionContext(state.context());
		SessionContext context = SessionContext.from(next, previous.fingerprint(), previous.expiresAt());
		long revision = state.revision() + 1;
		long generation = rotatedGeneration(sessions.rotate(binding.id(), credential.generation(), binding.revision(),
				"!", revision, context.serialize()), credential.generation());
		return issueAccess(next, context, binding.id(), generation, revision);
	}

	private long rotatedGeneration(Rotation result, long presented) {
		return switch (result) {
			case NEXT -> presented + 1;
			case CURRENT -> presented;
			case CONFLICT -> throw sessionConflict();
			case INVALID, REUSED -> throw invalidRefresh();
		};
	}

	private AuthResult issueAccess(GymOsPrincipal principal, SessionContext context, UUID id, long generation, long revision) {
		GymOsPrincipal bound = principal.withSession(new SessionBinding(id, revision, context.fingerprint()));
		JwtService.TokenPair tokens = jwtService.issueTokens(bound, refreshCredentials.issue(id, generation));
		Instant cookieExpiry = principal.supportMode() && principal.supportExpiresAt().isBefore(context.expiresAt())
				? principal.supportExpiresAt() : context.expiresAt();
		return new AuthResult(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn(), cookieExpiry,
				toResponse(bound));
	}

	private GymOsPrincipal restoreOrRevoke(SessionContext context, UUID id, long revision) {
		try {
			return restorePrincipalFor(context);
		} catch (ApiAuthenticationException invalid) {
			sessions.revokeContext(id, revision);
			throw invalid;
		}
	}

	private GymOsPrincipal restorePrincipalFor(SessionContext session) {
		AuthenticatedUser user = activeUser(session.userId());
		if (session.credentialVersion() != user.credentialVersion()
				|| !session.fingerprint().equals(fingerprint(user))
				|| !Instant.now().isBefore(session.expiresAt())) throw invalidRefresh();
		if (user.accountScope() != session.accountScope()) {
			throw invalidRefresh();
		}
		if (session.accountScope() == AccountScope.PLATFORM) {
			if (!user.platformAdmin()) {
				throw invalidRefresh();
			}
			if (!session.supportMode()) {
				if (session.organizationId() != null || session.branchId() != null || session.supportExpiresAt() != null) {
					throw invalidRefresh();
				}
				return user.toPrincipal(null);
			}
			if (session.organizationId() == null || session.supportExpiresAt() == null
					|| !Instant.now().isBefore(session.supportExpiresAt())
					|| !authQueryRepository.organizationIsActive(session.organizationId())) {
				throw invalidRefresh();
			}
			if (session.branchId() != null && !authQueryRepository.branchBelongsToOrganization(
					session.branchId(), session.organizationId())) {
				throw invalidRefresh();
			}
			return user.toSupportPrincipal(
					session.organizationId(), session.branchId(), session.supportExpiresAt());
		}

		if (session.supportMode() || session.supportExpiresAt() != null
				|| !user.organizationId().equals(session.organizationId())
				|| !authQueryRepository.organizationIsActive(user.organizationId())) {
			throw invalidRefresh();
		}
		if (session.branchId() != null
				&& (!authQueryRepository.branchBelongsToOrganization(session.branchId(), user.organizationId())
				|| !authQueryRepository.userHasBranchAccess(user.userId(), session.branchId()))) {
			throw invalidRefresh();
		}
		return user.toPrincipal(session.branchId());
	}

	private SessionContext parseSessionContext(String storedSession) {
		try {
			String[] parts = storedSession.split("\\|", -1);
			if (parts.length != 9) throw invalidRefresh();
			return new SessionContext(
					UUID.fromString(parts[0]), AccountScope.valueOf(parts[1]),
					parts[2].isBlank() ? null : UUID.fromString(parts[2]),
					parts[3].isBlank() ? null : UUID.fromString(parts[3]),
					Boolean.parseBoolean(parts[4]),
					parts[5].isBlank() ? null : Instant.ofEpochSecond(Long.parseLong(parts[5])),
					Long.parseLong(parts[6]), parts[7], Instant.ofEpochMilli(Long.parseLong(parts[8])));
		} catch (RuntimeException exception) {
			throw invalidRefresh();
		}
	}

	private String fingerprint(AuthenticatedUser user) {
		return new UserAuthorities(user.permissions(), user.roles()).fingerprint();
	}

	private AuthenticatedUser activeUser(UUID userId) {
		return authQueryRepository.findActiveUserById(userId).orElseThrow(this::invalidRefresh);
	}

	private UUID defaultBranchFor(AuthenticatedUser user) {
		if (user.accountScope() == AccountScope.PLATFORM) return null;
		return authQueryRepository.firstAccessibleBranch(user.userId(), user.organizationId()).orElse(null);
	}

	private CurrentUserResult toResponse(GymOsPrincipal principal) {
		AuthQueryRepository.OrganizationTheme theme = authQueryRepository.organizationTheme(principal.organizationId())
				.orElse(new AuthQueryRepository.OrganizationTheme(
						"GymFort", null, null, "#093D3A", "#18C8B2", "#F2F8F7"));
		return new CurrentUserResult(
				principal.userId(), principal.accountScope(), principal.organizationId(), principal.branchId(),
				principal.username(), principal.fullName(), principal.permissions(), principal.roles(),
				principal.platformIdentity(), principal.supportMode(), principal.supportExpiresAt(), console(principal),
				theme.organizationName(), theme.brandName(), theme.logoUrl(),
				theme.primaryColor(), theme.accentColor(), theme.surfaceColor());
	}

	private String console(GymOsPrincipal principal) {
		if (principal.platformAdmin()) return "PLATFORM_OWNER";
		if (principal.platformIdentity() && principal.supportMode()) return "TENANT_OWNER";
		if (hasRole(principal, "dueno") || hasRole(principal, "owner") || hasRole(principal, "administrador")) {
			return "TENANT_OWNER";
		}
		if (hasRole(principal, "gerente")) return "TENANT_MANAGER";
		if (principal.permissions().contains("sale.create")
				|| principal.permissions().contains("payment.manual.create")
				|| principal.permissions().contains("client.create")) return "RECEPTION";
		if (principal.permissions().contains("coach.read") || principal.permissions().contains("workout.manage")) return "COACH";
		return "TENANT_MANAGER";
	}

	private boolean hasRole(GymOsPrincipal principal, String fragment) {
		return principal.roles().stream()
				.map(role -> role.toLowerCase(java.util.Locale.ROOT))
				.anyMatch(role -> role.contains(fragment));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private void requirePlatformAdmin(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (!principal.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "Solo Admin GymFort puede iniciar soporte a un tenant.");
		}
	}

	private void requireActiveSupport(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (!principal.platformIdentity() || !principal.supportMode()
				|| principal.supportExpiresAt() == null
				|| !Instant.now().isBefore(principal.supportExpiresAt())) {
			throw new BusinessException("PLATFORM_SUPPORT_REQUIRED", "Inicia una sesion temporal de soporte para continuar.");
		}
	}

	private String normalizeSupportReason(String reason) {
		String normalized = reason == null ? "" : reason.trim();
		if (normalized.length() < 8) {
			throw new BusinessException("PLATFORM_SUPPORT_REASON_REQUIRED", "Explica brevemente el motivo del acceso de soporte.");
		}
		return normalized;
	}

	private ApiAuthenticationException invalidRefresh() {
		return new ApiAuthenticationException("REFRESH_TOKEN_INVALID", "Tu sesión terminó. Inicia sesión de nuevo.");
	}

	private static ApiConflictException sessionConflict() {
		return new ApiConflictException("SESSION_CHANGED", "La sesión cambió. Intenta de nuevo.");
	}

}
