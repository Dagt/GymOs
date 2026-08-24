package com.gymos.security.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
class AuthBranchOrganizationGuard {
	private static final String REFRESH_PREFIX = "gymos:refresh:v2:";

	private final AuthQueryRepository authQueryRepository;
	private final StringRedisTemplate redisTemplate;

	AuthBranchOrganizationGuard(AuthQueryRepository authQueryRepository, StringRedisTemplate redisTemplate) {
		this.authQueryRepository = authQueryRepository;
		this.redisTemplate = redisTemplate;
	}

	@Before("execution(* com.gymos.security.application.AuthService.selectBranch(..)) && args(principal,branchId,..)")
	void guardBranchSelection(GymOsPrincipal principal, UUID branchId) {
		if (principal == null || branchId == null) {
			return;
		}
		ensureBranchBelongsToOrganization(branchId, principal.organizationId(), "BRANCH_ACCESS_DENIED");
	}

	@Before("execution(* com.gymos.security.application.AuthService.refresh(..)) && args(refreshToken)")
	void guardRefreshSession(String refreshToken) {
		String storedSession = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
		if (storedSession == null || !storedSession.contains("|")) {
			return;
		}
		try {
			String[] parts = storedSession.split("\\|", -1);
			if (parts.length != 7 || !"v2".equals(parts[0]) || parts[4].isBlank()) {
				return;
			}
			UUID organizationId = UUID.fromString(parts[3]);
			UUID branchId = UUID.fromString(parts[4]);
			ensureBranchBelongsToOrganization(branchId, organizationId, "REFRESH_TOKEN_INVALID");
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("REFRESH_TOKEN_INVALID", "La sesion ya no es valida.");
		}
	}

	private void ensureBranchBelongsToOrganization(UUID branchId, UUID organizationId, String errorCode) {
		if (!authQueryRepository.branchBelongsToOrganization(branchId, organizationId)) {
			String message = "REFRESH_TOKEN_INVALID".equals(errorCode)
					? "La sesion ya no es valida."
					: "La sucursal no pertenece al gimnasio activo.";
			throw new BusinessException(errorCode, message);
		}
	}
}
