package com.gymos.communications.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.communications.infrastructure.OutboundDeliveryAdministrationRepository;
import com.gymos.communications.infrastructure.OutboundDeliveryAdministrationRepository.OutboundDeliveryRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundDeliveryAdministrationService {
    private final OutboundDeliveryAdministrationRepository repository;
    private final AuditService auditService;

    public OutboundDeliveryAdministrationService(
            OutboundDeliveryAdministrationRepository repository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<OutboundDeliveryRecord> search(
            GymOsPrincipal principal,
            String channel,
            String status,
            UUID clientId,
            UUID ruleId,
            int limit
    ) {
        requireRead(principal);
        return repository.search(
                principal.organizationId(), principal.branchId(), channel, status, clientId, ruleId, limit
        );
    }

    @Transactional
    public void retry(GymOsPrincipal principal, UUID messageId, String correlationId) {
        requireManage(principal);
        if (!repository.retry(principal.organizationId(), principal.branchId(), messageId)) {
            throw new BusinessException(
                    "COMMUNICATION_DELIVERY_NOT_RETRYABLE",
                    "La entrega no existe, pertenece a otro contexto o no puede reintentarse."
            );
        }
        auditService.record(principal, "COMMUNICATION_DELIVERY_RETRIED", "OUTBOUND_MESSAGE", messageId,
                "BACKOFFICE", "manual-retry", correlationId);
    }

    @Transactional
    public void cancel(GymOsPrincipal principal, UUID messageId, String correlationId) {
        requireManage(principal);
        if (!repository.cancel(principal.organizationId(), principal.branchId(), messageId)) {
            throw new BusinessException(
                    "COMMUNICATION_DELIVERY_NOT_CANCELLABLE",
                    "La entrega no existe, pertenece a otro contexto o no puede cancelarse."
            );
        }
        auditService.record(principal, "COMMUNICATION_DELIVERY_CANCELLED", "OUTBOUND_MESSAGE", messageId,
                "BACKOFFICE", "manual-cancel", correlationId);
    }

    private void requireRead(GymOsPrincipal principal) {
        if (principal == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión.");
        if (!principal.permissions().contains("automation.read")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para consultar comunicaciones.");
        }
    }

    private void requireManage(GymOsPrincipal principal) {
        requireRead(principal);
        if (!principal.permissions().contains("automation.retry")
                && !principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para operar entregas.");
        }
    }
}
