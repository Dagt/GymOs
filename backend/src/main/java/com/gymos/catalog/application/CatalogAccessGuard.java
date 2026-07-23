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
}
