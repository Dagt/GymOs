package com.gymos.reporting.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.reporting.infrastructure.OwnerDashboardRepository;
import com.gymos.reporting.infrastructure.OwnerDashboardRepository.OwnerDashboardRecord;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerDashboardService {
	private final OwnerDashboardRepository ownerDashboardRepository;

	public OwnerDashboardService(OwnerDashboardRepository ownerDashboardRepository) {
		this.ownerDashboardRepository = ownerDashboardRepository;
	}

	@Transactional(readOnly = true)
	public OwnerDashboardRecord dashboard(GymOsPrincipal principal, LocalDate fromDate, LocalDate toDate) {
		requireBranch(principal);
		LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
		LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusDays(29) : fromDate;
		if (resolvedFrom.isAfter(resolvedTo)) {
			throw new BusinessException("OWNER_DASHBOARD_RANGE_INVALID", "La fecha inicial no puede ser posterior a la final.");
		}
		if (resolvedFrom.plusDays(370).isBefore(resolvedTo)) {
			throw new BusinessException("OWNER_DASHBOARD_RANGE_TOO_LARGE", "El dashboard no puede cubrir mas de 370 dias.");
		}
		return ownerDashboardRepository.dashboard(principal.organizationId(), principal.branchId(), resolvedFrom, resolvedTo);
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
