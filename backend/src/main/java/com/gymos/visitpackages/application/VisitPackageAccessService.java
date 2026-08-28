package com.gymos.visitpackages.application;

import com.gymos.common.api.BusinessException;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitPackageAccessService implements VisitPackageAccessOperations {
	private final VisitPackageEntitlementPort entitlements;

	public VisitPackageAccessService(VisitPackageEntitlementPort entitlements) {
		this.entitlements = entitlements;
	}

	@Override
	@Transactional
	public Optional<VisitPackageAccessResult> consumeForAccess(
			UUID organizationId, UUID branchId, UUID clientId, List<UUID> requiredBenefitIds,
			UUID accessDeviceId, String idempotencyKey, LocalDate businessDate, OffsetDateTime occurredAt
	) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 160) {
			throw new BusinessException(
					"VISIT_PACKAGE_ACCESS_KEY_INVALID",
					"No fue posible identificar de forma segura el evento de acceso.");
		}
		return entitlements.consumeForAccess(
				organizationId, branchId, clientId,
				requiredBenefitIds == null ? List.of() : List.copyOf(requiredBenefitIds),
				accessDeviceId, idempotencyKey.trim(), businessDate, occurredAt);
	}

	@Override
	@Transactional
	public void linkAccessLog(UUID organizationId, UUID consumptionId, UUID accessLogId) {
		if (!entitlements.linkAccessLog(organizationId, consumptionId, accessLogId)) {
			throw new IllegalStateException("No fue posible vincular el consumo con la decisión de acceso.");
		}
	}
}
