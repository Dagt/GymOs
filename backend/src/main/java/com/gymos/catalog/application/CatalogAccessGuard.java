package com.gymos.catalog.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;

final class CatalogAccessGuard {
	private CatalogAccessGuard() {
	}

	static void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	static void requireBranch(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para administrar productos y planes.");
		}
	}
}
