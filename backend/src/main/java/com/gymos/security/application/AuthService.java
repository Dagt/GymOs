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
		String userId = redisTemplate.opsForValue().get(refreshKey(refreshToken));
		if (userId == null) {
			throw new BusinessException("REFRESH_TOKEN_INVALID", "La sesion ya no es valida.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(UUID.fromString(userId))
				.orElseThrow(() -> new BusinessException("REFRESH_TOKEN_INVALID", "La sesion ya no es valida."));
		if (!user.permissions().contains("platform.admin") && !authQueryRepository.organizationIsActive(user.organizationId())) {
			throw new BusinessException("ORGANIZATION_SUSPENDED", "El gimnasio no tiene acceso activo a la plataforma.");
		}
		return issue(user.toPrincipal(defaultBranchFor(user, user.organizationId())));
	}

	@Transactional
	public void logout(GymOsPrincipal principal, String refreshToken, String correlationId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		redisTemplate.delete(refreshKey(refreshToken));
		auditService.record(principal, "AUTH_LOGOUT", "USER", principal.userId(), "BACKOFFICE", null, correlationId);
	}

	@Transactional
	public AuthResult selectBranch(GymOsPrincipal principal, UUID branchId, String correlationId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		boolean supportAccess = principal.platformAdmin()
				&& principal.supportMode()
				&& authQueryRepository.branchBelongsToOrganization(branchId, principal.organizationId());
		if (!supportAccess && !authQueryRepository.userHasBranchAccess(principal.userId(), branchId)) {
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
		boolean supportAccess = principal.platformAdmin();
		if (!supportAccess && !authQueryRepository.userHasOrganizationAccess(principal.userId(), organizationId)) {
			throw new BusinessException("ORGANIZATION_ACCESS_DENIED", "No tienes acceso al gimnasio solicitado.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(principal.userId())
				.orElseThrow(() -> new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar."));
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
		redisTemplate.opsForValue().set(refreshKey(refreshToken), principal.userId().toString(), Duration.ofDays(7));
		JwtService.TokenPair tokens = jwtService.issueTokens(principal, refreshToken);
		return new AuthResult(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn(), toResponse(principal));
	}

	private UUID defaultBranchFor(AuthenticatedUser user, UUID organizationId) {
		if (user.permissions().contains("platform.admin")) {
			return null;
		}
		return authQueryRepository.firstAccessibleBranch(user.userId(), organizationId).orElse(null);
	}

	private CurrentUserResult toResponse(GymOsPrincipal principal) {
		AuthQueryRepository.OrganizationTheme theme = authQueryRepository.organizationTheme(principal.organizationId())
				.orElse(new AuthQueryRepository.OrganizationTheme("GymOS", null, null, "#164E63", "#F59E0B", "#F4F7FB"));
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
}
