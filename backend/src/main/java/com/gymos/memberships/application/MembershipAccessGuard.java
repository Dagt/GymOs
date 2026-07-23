package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;

final class MembershipAccessGuard {
	private MembershipAccessGuard() {
	}

	static void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
