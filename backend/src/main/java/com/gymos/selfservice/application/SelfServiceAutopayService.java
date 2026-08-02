package com.gymos.selfservice.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.RecurringPaymentProvider;
import com.gymos.payments.api.RecurringPaymentProvider.SetupRequest;
import com.gymos.payments.api.RecurringPaymentProvider.SetupResult;
import com.gymos.selfservice.infrastructure.SelfServiceAutopayRepository;
import com.gymos.selfservice.infrastructure.SelfServiceAutopayRepository.MandateRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfServiceAutopayService {
    private static final String CONSENT_VERSION = "2026-08-v1";

    private final SelfServiceSessionAccess sessionAccess;
    private final SelfServiceCapabilityPolicy capabilityPolicy;
    private final SelfServiceRepository selfServiceRepository;
    private final SelfServiceAutopayRepository autopayRepository;
    private final RecurringPaymentProvider recurringProvider;
    private final AuditService auditService;

    public SelfServiceAutopayService(
            SelfServiceSessionAccess sessionAccess,
            SelfServiceCapabilityPolicy capabilityPolicy,
            SelfServiceRepository selfServiceRepository,
            SelfServiceAutopayRepository autopayRepository,
            RecurringPaymentProvider recurringProvider,
            AuditService auditService
    ) {
        this.sessionAccess = sessionAccess;
        this.capabilityPolicy = capabilityPolicy;
        this.selfServiceRepository = selfServiceRepository;
        this.autopayRepository = autopayRepository;
        this.recurringProvider = recurringProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MandateRecord> mandates(UUID sessionId, String sessionToken) {
        SessionRecord session = requireAutopay(sessionId, sessionToken);
        return autopayRepository.mandates(session.organizationId(), session.branchId(), session.clientId());
    }

    @Transactional
    public SetupResponse startSetup(
            UUID sessionId,
            String sessionToken,
            UUID membershipId,
            boolean consentAccepted,
            String idempotencyKey,
            String successUrl,
            String cancelUrl,
            String correlationId
    ) {
        SessionRecord session = requireAutopay(sessionId, sessionToken);
        if (!consentAccepted) {
            throw new BusinessException("AUTOPAY_CONSENT_REQUIRED", "Debes aceptar el consentimiento de domiciliacion.");
        }
        requireMembership(session, membershipId);
        if (!recurringProvider.available()) {
            throw new BusinessException(
                    "RECURRING_PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "La domiciliacion aun no esta configurada para este entorno."
            );
        }
        String key = required(idempotencyKey, "AUTOPAY_IDEMPOTENCY_REQUIRED");
        SetupResult setup = recurringProvider.createSetup(new SetupRequest(
                key,
                session.clientId().toString(),
                "Domiciliacion de membresia " + membershipId,
                Map.of(
                        "sessionId", session.id().toString(),
                        "clientId", session.clientId().toString(),
                        "membershipId", membershipId.toString()
                ),
                successUrl,
                cancelUrl
        ));
        MandateRecord mandate = autopayRepository.createPending(
                session.organizationId(), session.branchId(), session.clientId(), membershipId,
                CONSENT_VERSION, OffsetDateTime.now()
        );
        autopayRepository.addEvent(
                mandate.id(), "PROVIDER_SETUP_CREATED", setup.providerSetupId(), "{}"
        );
        auditService.recordSystem(
                session.organizationId(), session.branchId(), null,
                "SELF_SERVICE_AUTOPAY_SETUP_STARTED", "SELF_SERVICE_AUTOPAY_MANDATE", mandate.id(),
                "SELF_SERVICE_KIOSK", recurringProvider.provider(), correlationId
        );
        return new SetupResponse(
                mandate,
                recurringProvider.provider(),
                setup.providerSetupId(),
                setup.checkoutUrl(),
                setup.clientSecret(),
                setup.expiresAt()
        );
    }

    @Transactional
    public MandateRecord revoke(
            UUID sessionId,
            String sessionToken,
            UUID mandateId,
            String reason,
            String correlationId
    ) {
        SessionRecord session = requireAutopay(sessionId, sessionToken);
        MandateRecord mandate = autopayRepository.mandate(
                session.organizationId(), session.branchId(), session.clientId(), mandateId
        ).orElseThrow(() -> new BusinessException(
                "AUTOPAY_MANDATE_NOT_FOUND", "La domiciliacion no existe o no esta disponible."
        ));
        if (mandate.providerPaymentMethodId() != null && recurringProvider.available()) {
            recurringProvider.revoke(mandate.providerPaymentMethodId());
        }
        if (!autopayRepository.revoke(mandateId, blankToNull(reason))) {
            throw new BusinessException("AUTOPAY_MANDATE_NOT_REVOCABLE", "La domiciliacion ya no puede revocarse.");
        }
        auditService.recordSystem(
                session.organizationId(), session.branchId(), null,
                "SELF_SERVICE_AUTOPAY_REVOKED", "SELF_SERVICE_AUTOPAY_MANDATE", mandateId,
                "SELF_SERVICE_KIOSK", blankToNull(reason), correlationId
        );
        return autopayRepository.mandate(mandateId).orElseThrow();
    }

    public boolean providerReady() {
        return recurringProvider.available();
    }

    private SessionRecord requireAutopay(UUID sessionId, String sessionToken) {
        SessionRecord session = sessionAccess.verified(sessionId, sessionToken);
        capabilityPolicy.require(
                capabilityPolicy.resolve(session.allowedActionsJson()),
                SelfServiceCapability.AUTOPAY
        );
        return session;
    }

    private void requireMembership(SessionRecord session, UUID membershipId) {
        if (membershipId == null || selfServiceRepository.memberships(
                session.organizationId(), session.branchId(), session.clientId()
        ).stream().noneMatch(item -> item.id().equals(membershipId))) {
            throw new BusinessException(
                    "SELF_SERVICE_MEMBERSHIP_NOT_FOUND",
                    "La membresia no pertenece al socio verificado."
            );
        }
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, "Falta un dato requerido para continuar.");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SetupResponse(
            MandateRecord mandate,
            String provider,
            String providerSetupId,
            String checkoutUrl,
            String clientSecret,
            OffsetDateTime expiresAt
    ) {}
}
