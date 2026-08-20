package com.gymos.security.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.security.application.result.AuthResult;
import com.gymos.security.application.result.CurrentUserResult;
import com.gymos.security.infrastructure.AuthQueryRepository;
import com.gymos.security.infrastructure.AuthQueryRepository.AuthenticatedUser;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private static final String REFRESH_PREFIX = "gymos:refresh:";
	private static final String SESSION_SEPARATOR = "|";
	private static final String ROTATED_PREFIX = "rotated:";
	private static final Duration ROTATION_GRACE = Duration.ofSeconds(15);

	private final AuthenticationManager authenticationManager;
	private final AuthQueryRepository authQueryRepository;
	private final JwtService jwtService;
	private final StringRedisTemplate redisTemplate;
	private final AuditService auditService;

	public AuthService(
			AuthenticationManager authenticationManager,
			AuthQueryRepository authQueryRepository,
			JwtService jwtService,
			StringRedisTemplate redisTemplate,
			AuditService auditService
	) {
		this.authenticationManager = authenticationManager;
		this.authQueryRepository = authQueryRepository;
		this.jwtService = jwtService;
		this.redisTemplate = redisTemplate;
		this.auditService = auditService;
	}

	@Transactional
	public AuthResult login(String username, String password, String correlationId) {
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		AuthenticatedUser user = authQueryRepository.findActiveUserByUsername(username)
				.orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Usuario o password invalidos."));
		if (!user.permissions().contains("platform.admin") && !authQueryRepository.organizationIsActive(user.organizationId())) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		GymOsPrincipal principal = user.toPrincipal(defaultBranchFor(user, user.organizationId()));
		AuthResult response = issue(principal);
		auditService.recordSystem(principal.organizationId(), null, principal.userId(), "AUTH_LOGIN", "USER", principal.userId(), "BACKOFFICE", null, correlationId);
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
			GymOsPrincipal principal = restorePrincipalFor(replacementSession);
			return issueAccess(principal, replacementToken);
		}

		GymOsPrincipal principal = restorePrincipalFor(storedValue);
		AuthResult rotated = issue(principal);
		redisTemplate.opsForValue().set(
				refreshKey(refreshToken),
				ROTATED_PREFIX + rotated.refreshToken(),
				ROTATION_GRACE
		);
		return rotated;
	}

	@Transactional
	public void logout(GymOsPrincipal principal, String refreshToken, String correlationId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		String storedValue = redisTemplate.opsForValue().get(refreshKey(refreshToken));
		if (storedValue != null) {
			String storedSession = storedValue;
			String replacementToken = null;
			if (storedValue.startsWith(ROTATED_PREFIX)) {
				replacementToken = storedValue.substring(ROTATED_PREFIX.length());
				storedSession = redisTemplate.opsForValue().get(refreshKey(replacementToken));
			}
			if (storedSession != null && !storedSession.startsWith(ROTATED_PREFIX)
					&& parseRefreshSession(storedSession).userId().equals(principal.userId())) {
				redisTemplate.delete(refreshKey(refreshToken));
				if (replacementToken != null) {
					redisTemplate.delete(refreshKey(replacementToken));
				}
			}
		}
		auditService.record(principal, "AUTH_LOGOUT", "USER", principal.userId(), "BACKOFFICE", null, correlationId);
	}

	@Transactional
	public AuthResult selectBranch(GymOsPrincipal principal, UUID branchId, String correlationId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		boolean branchInOrganization = authQueryRepository.branchBelongsToOrganization(branchId, principal.organizationId());
		boolean supportAccess = principal.platformAdmin() && principal.supportMode() && branchInOrganization;
		boolean standardAccess = branchInOrganization && authQueryRepository.userHasBranchAccess(principal.userId(), branchId);
		if (!supportAccess && !standardAccess) {
			throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(principal.userId())
				.orElseThrow(() -> new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar."));
		GymOsPrincipal selectedPrincipal = new GymOsPrincipal(
				user.userId(),
				principal.organizationId(),
				branchId,
				user.username(),
				user.fullName(),
				user.permissions(),
				user.roles(),
				principal.supportMode()
		);
		AuthResult response = issue(selectedPrincipal);
		auditService.recordSystem(
				principal.organizationId(),
				branchId,
				principal.userId(),
				"BRANCH_SELECTED",
				"BRANCH",
				branchId,
				principal.supportMode() ? "PLATFORM_SUPPORT" : "BACKOFFICE",
				null,
				correlationId
		);
		return response;
	}

	@Transactional
	public AuthResult selectOrganization(GymOsPrincipal principal, UUID organizationId, String correlationId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (!authQueryRepository.organizationIsActive(organizationId)) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(principal.userId())
				.orElseThrow(() -> new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar."));
		boolean supportAccess = principal.platformAdmin();
		boolean homeOrganization = organizationId.equals(user.organizationId());
		if (!supportAccess && !homeOrganization && !authQueryRepository.userHasOrganizationAccess(principal.userId(), organizationId)) {
			throw new BusinessException("ORGANIZATION_ACCESS_DENIED", "No tienes acceso al gimnasio solicitado.");
		}
		UUID branchId = supportAccess
				? authQueryRepository.firstActiveBranch(organizationId).orElse(null)
				: defaultBranchFor(user, organizationId);
		GymOsPrincipal selectedPrincipal = new GymOsPrincipal(
				user.userId(),
				organizationId,
				branchId,
				user.username(),
				user.fullName(),
				user.permissions(),
				user.roles(),
				supportAccess
		);
		AuthResult response = issue(selectedPrincipal);
		auditService.recordSystem(
				organizationId,
				branchId,
				principal.userId(),
				supportAccess ? "PLATFORM_SUPPORT_STARTED" : "ORGANIZATION_SELECTED",
				"ORGANIZATION",
				organizationId,
				supportAccess ? "PLATFORM_SUPPORT" : "BACKOFFICE",
				null,
				correlationId
		);
		return response;
	}

	public CurrentUserResult currentUser(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		return toResponse(principal);
	}

	private AuthResult issue(GymOsPrincipal principal) {
		String refreshToken = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(
				refreshKey(refreshToken),
				new RefreshSession(principal.userId(), principal.organizationId(), principal.branchId(), principal.supportMode()).serialize(),
				Duration.ofDays(7)
		);
		return issueAccess(principal, refreshToken);
	}

	private AuthResult issueAccess(GymOsPrincipal principal, String refreshToken) {
		JwtService.TokenPair tokens = jwtService.issueTokens(principal, refreshToken);
		return new AuthResult(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn(), toResponse(principal));
	}

	private GymOsPrincipal restorePrincipalFor(String storedSession) {
		RefreshSession session = parseRefreshSession(storedSession);
		AuthenticatedUser user = authQueryRepository.findActiveUserById(session.userId())
				.orElseThrow(this::invalidRefresh);
		return restorePrincipal(user, session);
	}

	private GymOsPrincipal restorePrincipal(AuthenticatedUser user, RefreshSession session) {
		boolean platformAdmin = user.permissions().contains("platform.admin");
		if ((!platformAdmin || session.supportMode()) && !authQueryRepository.organizationIsActive(session.organizationId())) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		boolean homeOrganization = session.organizationId().equals(user.organizationId());
		if (!platformAdmin && !homeOrganization
				&& !authQueryRepository.userHasOrganizationAccess(user.userId(), session.organizationId())) {
			throw invalidRefresh();
		}
		if (session.supportMode() && !platformAdmin) {
			throw invalidRefresh();
		}
		if (session.branchId() != null) {
			boolean branchInOrganization = authQueryRepository.branchBelongsToOrganization(
					session.branchId(), session.organizationId());
			boolean branchAllowed = session.supportMode()
					? branchInOrganization
					: branchInOrganization && authQueryRepository.userHasBranchAccess(user.userId(), session.branchId());
			if (!branchAllowed) {
				throw invalidRefresh();
			}
		}
		return new GymOsPrincipal(
				user.userId(),
				session.organizationId(),
				session.branchId(),
				user.username(),
				user.fullName(),
				user.permissions(),
				user.roles(),
				session.supportMode()
		);
	}

	private RefreshSession parseRefreshSession(String storedSession) {
		try {
			if (!storedSession.contains(SESSION_SEPARATOR)) {
				UUID userId = UUID.fromString(storedSession);
				AuthenticatedUser user = authQueryRepository.findActiveUserById(userId)
						.orElseThrow(() -> new IllegalArgumentException("Unknown user"));
				return new RefreshSession(userId, user.organizationId(), defaultBranchFor(user, user.organizationId()), false);
			}
			String[] parts = storedSession.split("\\|", -1);
			if (parts.length != 4) {
				throw new IllegalArgumentException("Invalid refresh session");
			}
			return new RefreshSession(
					UUID.fromString(parts[0]),
					UUID.fromString(parts[1]),
					parts[2].isBlank() ? null : UUID.fromString(parts[2]),
					Boolean.parseBoolean(parts[3])
			);
		} catch (RuntimeException exception) {
			throw invalidRefresh();
		}
	}

	private BusinessException invalidRefresh() {
		return new BusinessException("REFRESH_TOKEN_INVALID", "La sesion ya no es valida.");
	}

	private UUID defaultBranchFor(AuthenticatedUser user, UUID organizationId) {
		if (user.permissions().contains("platform.admin")) {
			return null;
		}
		return authQueryRepository.firstAccessibleBranch(user.userId(), organizationId).orElse(null);
	}

	private CurrentUserResult toResponse(GymOsPrincipal principal) {
		AuthQueryRepository.OrganizationTheme theme = authQueryRepository.organizationTheme(principal.organizationId())
				.orElse(new AuthQueryRepository.OrganizationTheme("GymFort", null, null, "#093D3A", "#18C8B2", "#F2F8F7"));
		return new CurrentUserResult(
				principal.userId(),
				principal.organizationId(),
				principal.branchId(),
				principal.username(),
				principal.fullName(),
				principal.permissions(),
				principal.roles(),
				principal.platformAdmin(),
				principal.supportMode(),
				console(principal),
				theme.organizationName(),
				theme.brandName(),
				theme.logoUrl(),
				theme.primaryColor(),
				theme.accentColor(),
				theme.surfaceColor()
		);
	}

	private String console(GymOsPrincipal principal) {
		if (principal.platformAdmin() && !principal.supportMode()) {
			return "PLATFORM_OWNER";
		}
		if (hasRole(principal, "dueno") || hasRole(principal, "owner") || hasRole(principal, "administrador")) {
			return "TENANT_OWNER";
		}
		if (hasRole(principal, "gerente")) {
			return "TENANT_MANAGER";
		}
		if (principal.permissions().contains("sale.create")
				|| principal.permissions().contains("payment.manual.create")
				|| principal.permissions().contains("client.create")) {
			return "RECEPTION";
		}
		if (principal.permissions().contains("coach.read") || principal.permissions().contains("workout.manage")) {
			return "COACH";
		}
		return "TENANT_MANAGER";
	}

	private boolean hasRole(GymOsPrincipal principal, String fragment) {
		return principal.roles().stream()
				.map(role -> role.toLowerCase(java.util.Locale.ROOT))
				.anyMatch(role -> role.contains(fragment));
	}

	private String refreshKey(String refreshToken) {
		return REFRESH_PREFIX + refreshToken;
	}

	private record RefreshSession(UUID userId, UUID organizationId, UUID branchId, boolean supportMode) {
		private String serialize() {
			return userId + SESSION_SEPARATOR
					+ organizationId + SESSION_SEPARATOR
					+ (branchId == null ? "" : branchId) + SESSION_SEPARATOR
					+ supportMode;
		}
	}
}
