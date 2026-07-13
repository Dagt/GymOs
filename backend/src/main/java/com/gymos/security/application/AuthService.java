package com.gymos.security.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.security.api.AuthController.AuthResponse;
import com.gymos.security.api.AuthController.CurrentUserResponse;
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

	public AuthService(
			AuthenticationManager authenticationManager,
			AuthQueryRepository authQueryRepository,
			JwtService jwtService,
			StringRedisTemplate redisTemplate
	) {
		this.authenticationManager = authenticationManager;
		this.authQueryRepository = authQueryRepository;
		this.jwtService = jwtService;
		this.redisTemplate = redisTemplate;
	}

	@Transactional(readOnly = true)
	public AuthResponse login(String username, String password) {
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		AuthenticatedUser user = authQueryRepository.findActiveUserByUsername(username)
				.orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Usuario o contraseña inválidos."));
		return issue(user.toPrincipal(null));
	}

	@Transactional(readOnly = true)
	public AuthResponse refresh(String refreshToken) {
		String userId = redisTemplate.opsForValue().get(refreshKey(refreshToken));
		if (userId == null) {
			throw new BusinessException("REFRESH_TOKEN_INVALID", "La sesión ya no es válida.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(UUID.fromString(userId))
				.orElseThrow(() -> new BusinessException("REFRESH_TOKEN_INVALID", "La sesión ya no es válida."));
		return issue(user.toPrincipal(null));
	}

	public void logout(GymOsPrincipal principal, String refreshToken) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar.");
		}
		redisTemplate.delete(refreshKey(refreshToken));
	}

	@Transactional(readOnly = true)
	public AuthResponse selectBranch(GymOsPrincipal principal, UUID branchId) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar.");
		}
		if (!authQueryRepository.userHasBranchAccess(principal.userId(), branchId)) {
			throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
		}
		AuthenticatedUser user = authQueryRepository.findActiveUserById(principal.userId())
				.orElseThrow(() -> new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar."));
		return issue(user.toPrincipal(branchId));
	}

	public CurrentUserResponse currentUser(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar.");
		}
		return toResponse(principal);
	}

	private AuthResponse issue(GymOsPrincipal principal) {
		String refreshToken = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(refreshKey(refreshToken), principal.userId().toString(), Duration.ofDays(7));
		JwtService.TokenPair tokens = jwtService.issueTokens(principal, refreshToken);
		return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn(), toResponse(principal));
	}

	private CurrentUserResponse toResponse(GymOsPrincipal principal) {
		return new CurrentUserResponse(
				principal.userId(),
				principal.organizationId(),
				principal.branchId(),
				principal.username(),
				principal.fullName(),
				principal.permissions()
		);
	}

	private String refreshKey(String refreshToken) {
		return REFRESH_PREFIX + refreshToken;
	}
}
