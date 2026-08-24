package com.gymos.access.application;

import com.gymos.audit.api.AuditService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits rejected Connector authentication attempts independently of the rejected request. */
@Service
public class PhysicalConnectorSecurityAudit {
	private final AuditService auditService;

	public PhysicalConnectorSecurityAudit(AuditService auditService) {
		this.auditService = auditService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void authenticationRejected(
			UUID organizationId,
			UUID branchId,
			UUID entityId,
			String correlationId
	) {
		auditService.recordSystem(
				organizationId, branchId, null,
				"PHYSICAL_CONNECTOR_AUTH_FAILED", "ACCESS_PROVIDER_CONNECTION", entityId,
				"CONNECTOR", "Autenticación M2M rechazada", correlationId);
	}
}
