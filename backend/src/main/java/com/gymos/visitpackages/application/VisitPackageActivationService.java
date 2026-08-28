package com.gymos.visitpackages.application;

import com.gymos.audit.api.AuditService;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class VisitPackageActivationService {
	private final VisitPackageEntitlementPort entitlements;
	private final AuditService auditService;

	VisitPackageActivationService(VisitPackageEntitlementPort entitlements, AuditService auditService) {
		this.entitlements = entitlements;
		this.auditService = auditService;
	}

	@Transactional
	void activate(UUID organizationId, UUID branchId, UUID saleId, OffsetDateTime occurredAt, String eventKey) {
		var definition = entitlements.activationDefinition(organizationId, saleId);
		if (definition.isEmpty() || !"PENDING_PAYMENT".equals(definition.get().status())) return;
		OffsetDateTime effectiveAt = occurredAt == null ? OffsetDateTime.now() : occurredAt;
		if (!entitlements.activateBySale(
				organizationId, saleId, entitlements.businessDate(organizationId, branchId, effectiveAt), effectiveAt)) return;
		auditService.recordSystem(
				organizationId, branchId, null, "VISIT_PACKAGE_ACTIVATED", "VISIT_PACKAGE_ENTITLEMENT",
				definition.get().entitlementId(), "PAYMENT_COMPLETION", null, eventKey);
	}
}
