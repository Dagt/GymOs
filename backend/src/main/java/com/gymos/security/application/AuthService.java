package com.gymos.security.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.common.security.SecurityProperties;
import com.gymos.security.application.result.AuthResult;
import com.gymos.security.application.result.CurrentUserResult;
import com.gymos.security.infrastructure.AuthQueryRepository;
import com.gymos.security.infrastructure.AuthQueryRepository.AuthenticatedUser;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AuthService {
	private static final String REFRESH_PREFIX = "gymos:refresh:v2:";
	private static final String SESSION_SEPARATOR = "|";
	private static final String SESSION_VERSION = "v2";
	private static final String ROTATED_PREFIX = "rotated:";
	private static final Duration ROTATION_GRACE = Duration.ofSeconds(15);

	private final AuthenticationManager authenticationManager;
	private final AuthQueryRepository authQueryRepository;
	private final JwtService jwtService;
	private final StringRedisTemplate redisTemplate;
	private final AuditService auditService;
	private final SecurityProperties securityProperties;

	@Autowired
	public AuthService(
			AuthenticationManager authenticationManager,
			AuthQueryRepository authQueryRepository,
			JwtService jwtService,
			StringRedisTemplate redisTemplate,
			AuditService auditService,
			SecurityProperties securityProperties
	) {
		this.authenticationManager = authenticationManager;
		this.authQueryRepository = authQueryRepository;
		this.jwtService = jwtService;
		this.redisTemplate = redisTemplate;
		this.auditService = auditService;
		this.securityProperties = securityProperties;
	}

	AuthService(
			AuthenticationManager authenticationManager,
			AuthQueryRepository authQueryRepository,
			JwtService jwtService,
			StringRedisTemplate redisTemplate,
			AuditService auditService
	) {
		this(authenticationManager, authQueryRepository, jwtService, redisTemplate, auditService,
				new SecurityProperties(null, null, 900, 86_400, 7, 30));
	}

	@Transactional
	public AuthResult login(String username, String password, String correlationId) {
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
		AuthResult response = issue(principal);
		auditService.recordSystem(
				principal.organizationId(), null, principal.userId(), "AUTH_LOGIN", "USER",
				principal.userId(), "BACKOFFICE", null, correlationId);
		return response;
	}

	@Transactional(readOnly = true)
	public AuthResult refresh(String refreshToken) {
		String storedValue = redisTemplate.opsForValue().get(refreshKey(refreshToken));
		if (storedValue == null) {
			throw invalidRefresh();
		}
		if (storedValue.startsWith(ROTATED_PREFIX)) {
			String replacementToken = storedValue.substring(ROTATED_PREFIX.length());
			String replacementSession = redisTemplate.opsForValue().get(refreshKey(replacementToken));
			if (replacementSession == null || replacementSession.startsWith(ROTATED_PREFIX)) {
				throw invalidRefresh();
			}
			return issueAccess(restorePrincipalFor(replacementSession), replacementToken);
		}
		GymOsPrincipal principal = restorePrincipalFor(storedValue);
		AuthResult rotated = issue(principal);
		redisTemplate.opsForValue().set(
				refreshKey(refreshToken), ROTATED_PREFIX + rotated.refreshToken(), ROTATION_GRACE);
		return rotated;
	}

	@Transactional
	public void logout(GymOsPrincipal principal, String refreshToken, String correlationId) {
		requirePrincipal(principal);
		revokeOwnedRefresh(principal, refreshToken);
		auditService.record(principal, "AUTH_LOGOUT", "USER", principal.userId(), "BACKOFFICE", null, correlationId);
	}

	@Transactional
	public AuthResult selectBranch(GymOsPrincipal principal, UUID branchId, String correlationId) {
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
		AuthResult response = issue(selectedPrincipal);
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
		Instant expiresAt = Instant.now().plus(Duration.ofMinutes(securityProperties.platformSupportMinutes()));
		GymOsPrincipal supportPrincipal = user.toSupportPrincipal(organizationId, branchId, expiresAt);
		AuthResult response = issue(supportPrincipal);
		auditService.recordSystem(
				organizationId, branchId, principal.userId(), "PLATFORM_SUPPORT_STARTED", "ORGANIZATION",
				organizationId, "PLATFORM_SUPPORT", normalizedReason, correlationId);
		return response;
	}

	AuthResult selectOrganization(GymOsPrincipal principal, UUID organizationId, String correlationId) {
		return selectOrganization(principal, organizationId, "Legacy support request", correlationId);
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
		revokeOwnedRefresh(principal, refreshToken);
		GymOsPrincipal platformPrincipal = user.toPrincipal(null);
		AuthResult response = issue(platformPrincipal);
		auditService.recordSystem(
				principal.organizationId(), principal.branchId(), principal.userId(), "PLATFORM_SUPPORT_ENDED",
				"ORGANIZATION", principal.organizationId(), "PLATFORM_SUPPORT", null, correlationId);
		return response;
	}

	public CurrentUserResult currentUser(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return toResponse(principal);
	}

	private AuthResult issue(GymOsPrincipal principal) {
		String refreshToken = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(
				refreshKey(refreshToken), RefreshSession.from(principal).serialize(), refreshLifetime(principal));
		return issueAccess(principal, refreshToken);
	}

	private Duration refreshLifetime(GymOsPrincipal principal) {
		if (principal.supportMode()) {
			long seconds = Duration.between(Instant.now(), principal.supportExpiresAt()).toSeconds();
			if (seconds <= 0) {
				throw new BusinessException("PLATFORM_SUPPORT_EXPIRED", "La sesion de soporte termino. Vuelve al panel de plataforma.");
			}
			return Duration.ofSeconds(seconds);
		}
		return Duration.ofDays(securityProperties.refreshTokenDays());
	}

	private AuthResult issueAccess(GymOsPrincipal principal, String refreshToken) {
		JwtService.TokenPair tokens = jwtService.issueTokens(principal, refreshToken);
		return new AuthResult(
				tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn(), toResponse(principal));
	}

	private GymOsPrincipal restorePrincipalFor(String storedSession) {
		RefreshSession session = parseRefreshSession(storedSession);
		AuthenticatedUser user = activeUser(session.userId());
		if (session.credentialVersion() != user.credentialVersion()) throw invalidRefresh();
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

	private RefreshSession parseRefreshSession(String storedSession) {
		try {
			String[] parts = storedSession.split("\\|", -1);
			if ((parts.length != 7 && parts.length != 8) || !SESSION_VERSION.equals(parts[0])) {
				throw new IllegalArgumentException("Invalid refresh session version");
			}
			return new RefreshSession(
					UUID.fromString(parts[1]), AccountScope.valueOf(parts[2]),
					parts[3].isBlank() ? null : UUID.fromString(parts[3]),
					parts[4].isBlank() ? null : UUID.fromString(parts[4]),
					Boolean.parseBoolean(parts[5]),
					parts[6].isBlank() ? null : Instant.ofEpochSecond(Long.parseLong(parts[6])),
					parts.length == 8 ? Long.parseLong(parts[7]) : 0);
		} catch (RuntimeException exception) {
			throw invalidRefresh();
		}
	}

	private void revokeOwnedRefresh(GymOsPrincipal principal, String refreshToken) {
		String key = refreshKey(refreshToken);
		String storedValue = redisTemplate.opsForValue().get(key);
		if (storedValue == null) return;
		String storedSession = storedValue;
		String replacementToken = null;
		if (storedValue.startsWith(ROTATED_PREFIX)) {
			replacementToken = storedValue.substring(ROTATED_PREFIX.length());
			storedSession = redisTemplate.opsForValue().get(refreshKey(replacementToken));
		}
		if (storedSession != null && !storedSession.startsWith(ROTATED_PREFIX)
				&& parseRefreshSession(storedSession).userId().equals(principal.userId())) {
			redisTemplate.delete(key);
			if (replacementToken != null) redisTemplate.delete(refreshKey(replacementToken));
		}
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

	private BusinessException invalidRefresh() {
		return new BusinessException("REFRESH_TOKEN_INVALID", "La sesion ya no es valida.");
	}

	private String refreshKey(String refreshToken) {
		return REFRESH_PREFIX + refreshToken;
	}

	private record RefreshSession(
			UUID userId,
			AccountScope accountScope,
			UUID organizationId,
			UUID branchId,
			boolean supportMode,
			Instant supportExpiresAt,
			long credentialVersion
	) {
		private static RefreshSession from(GymOsPrincipal principal) {
			return new RefreshSession(
					principal.userId(), principal.accountScope(), principal.organizationId(), principal.branchId(),
					principal.supportMode(), principal.supportExpiresAt(), principal.credentialVersion());
		}

		private String serialize() {
			return SESSION_VERSION + SESSION_SEPARATOR + userId + SESSION_SEPARATOR
					+ accountScope.name() + SESSION_SEPARATOR
					+ (organizationId == null ? "" : organizationId) + SESSION_SEPARATOR
					+ (branchId == null ? "" : branchId) + SESSION_SEPARATOR
					+ supportMode + SESSION_SEPARATOR
					+ (supportExpiresAt == null ? "" : supportExpiresAt.getEpochSecond()) + SESSION_SEPARATOR + credentialVersion;
		}
	}
}
