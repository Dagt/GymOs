package com.gymos.selfservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.api.operations.AutomationDomainEvents.DomainEvent;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.EnrollMembershipOperationCommand;
import com.gymos.memberships.api.operations.MembershipEnrollmentOperations;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipQuoteOperations;
import com.gymos.memberships.api.operations.MembershipQuoteOperations.Quote;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.payments.api.OnlinePaymentGateway;
import com.gymos.payments.api.OnlinePaymentGateway.CreateIntentRequest;
import com.gymos.payments.api.OnlinePaymentGateway.CreateIntentResult;
import com.gymos.payments.api.OnlinePaymentGateway.WebhookResult;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.CreatePaymentIntentData;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.CreateQuoteData;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.PaymentIntentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.QuoteRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfServiceCheckoutService {
    private static final Duration QUOTE_TTL = Duration.ofMinutes(10);

    private final SelfServiceSessionAccess sessionAccess;
    private final SelfServiceRepository selfServiceRepository;
    private final SelfServiceCheckoutRepository checkoutRepository;
    private final MembershipQuoteOperations quoteOperations;
    private final MembershipOperations membershipOperations;
    private final MembershipEnrollmentOperations enrollmentOperations;
    private final SelfServiceCapabilityPolicy capabilityPolicy;
    private final OnlinePaymentGateway paymentGateway;
    private final AutomationDomainEvents automationDomainEvents;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SelfServiceCheckoutService(
            SelfServiceSessionAccess sessionAccess,
            SelfServiceRepository selfServiceRepository,
            SelfServiceCheckoutRepository checkoutRepository,
            MembershipQuoteOperations quoteOperations,
            MembershipOperations membershipOperations,
            MembershipEnrollmentOperations enrollmentOperations,
            SelfServiceCapabilityPolicy capabilityPolicy,
            OnlinePaymentGateway paymentGateway,
            AutomationDomainEvents automationDomainEvents,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.sessionAccess = sessionAccess;
        this.selfServiceRepository = selfServiceRepository;
        this.checkoutRepository = checkoutRepository;
        this.quoteOperations = quoteOperations;
        this.membershipOperations = membershipOperations;
        this.enrollmentOperations = enrollmentOperations;
        this.capabilityPolicy = capabilityPolicy;
        this.paymentGateway = paymentGateway;
        this.automationDomainEvents = automationDomainEvents;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuoteRecord createQuote(
            UUID sessionId,
            String sessionToken,
            String operationType,
            UUID membershipId,
            UUID planId,
            String correlationId
    ) {
        SessionRecord session = sessionAccess.verified(sessionId, sessionToken);
        String operation = normalizeOperation(operationType);
        SelfServiceCapabilities capabilities = capabilityPolicy.resolve(session.allowedActionsJson());
        UUID resolvedPlanId = planId;
        if ("RENEWAL".equals(operation)) {
            capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_RENEWAL);
            MembershipRecord membership = requireMembership(session, membershipId);
            resolvedPlanId = membership.planId();
        } else if ("PLAN_CHANGE".equals(operation)) {
            capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_PLAN_CHANGE);
            requireMembership(session, membershipId);
            if (resolvedPlanId == null) {
                throw new BusinessException("SELF_SERVICE_PLAN_REQUIRED", "Selecciona el plan destino.");
            }
        } else if ("ENROLLMENT".equals(operation)) {
            capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_PLAN_CHANGE);
            if (resolvedPlanId == null) {
                throw new BusinessException("SELF_SERVICE_PLAN_REQUIRED", "Selecciona el plan que deseas contratar.");
            }
            if (!selfServiceRepository.memberships(
                    session.organizationId(), session.branchId(), session.clientId()
            ).isEmpty()) {
                throw new BusinessException(
                        "SELF_SERVICE_ENROLLMENT_NOT_AVAILABLE",
                        "La membresia inicial solo esta disponible para socios sin membresias registradas."
                );
            }
            membershipId = null;
        } else {
            throw new BusinessException("SELF_SERVICE_OPERATION_UNSUPPORTED", "La operacion comercial no esta soportada.");
        }
        Quote quote = quoteOperations.quote(kioskPrincipal(session), session.clientId(), resolvedPlanId);
        QuoteRecord stored = checkoutRepository.createQuote(new CreateQuoteData(
                session.organizationId(), session.branchId(), session.id(), session.clientId(), membershipId,
                operation, quote.planId(), quote.planName(), quote.listPriceCents(), quote.discountCents(),
                quote.totalCents(), quote.currency(), benefitsJson(quote), OffsetDateTime.now().plus(QUOTE_TTL)
        ));
        auditService.recordSystem(
                session.organizationId(), session.branchId(), null,
                "SELF_SERVICE_QUOTE_CREATED", "SELF_SERVICE_QUOTE", stored.id(),
                "SELF_SERVICE_KIOSK", operation, correlationId
        );
        return stored;
    }

    @Transactional
    public PaymentIntentRecord createPaymentIntent(
            UUID sessionId,
            String sessionToken,
            UUID quoteId,
            String idempotencyKey,
            String successUrl,
            String cancelUrl,
            String correlationId
    ) {
        SessionRecord session = sessionAccess.verified(sessionId, sessionToken);
        QuoteRecord quote = checkoutRepository.activeQuote(quoteId, sessionId, OffsetDateTime.now())
                .orElseThrow(() -> new BusinessException(
                        "SELF_SERVICE_QUOTE_INVALID", "La cotizacion no existe, expiro o ya fue utilizada."
                ));
        if (!quote.clientId().equals(session.clientId())) {
            throw new BusinessException("SELF_SERVICE_QUOTE_INVALID", "La cotizacion no pertenece a esta sesion.");
        }
        if (!paymentGateway.available()) {
            throw new BusinessException(
                    "ONLINE_PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Los pagos con tarjeta aun no estan configurados para este entorno."
            );
        }
        String key = required(idempotencyKey, "SELF_SERVICE_IDEMPOTENCY_REQUIRED");
        PaymentIntentRecord existing = checkoutRepository.paymentIntentByIdempotency(session.organizationId(), key)
                .orElse(null);
        if (existing != null) return existing;

        CreateIntentResult providerIntent = paymentGateway.createIntent(new CreateIntentRequest(
                key, quote.totalCents(), quote.currency(), quote.operationType() + " - " + quote.planName(),
                Map.of(
                        "quoteId", quote.id().toString(),
                        "sessionId", session.id().toString(),
                        "clientId", session.clientId().toString()
                ), successUrl, cancelUrl
        ));
        PaymentIntentRecord intent = checkoutRepository.createPaymentIntent(new CreatePaymentIntentData(
                session.organizationId(), session.branchId(), session.id(), session.clientId(), quote.id(),
                paymentGateway.provider(), providerIntent.providerIntentId(), key, quote.totalCents(), quote.currency(),
                normalizeProviderStatus(providerIntent.status()), providerIntent.checkoutUrl(),
                providerIntent.clientSecret(), providerIntent.expiresAt()
        ));
        auditService.recordSystem(
                session.organizationId(), session.branchId(), null,
                "SELF_SERVICE_PAYMENT_INTENT_CREATED", "SELF_SERVICE_PAYMENT_INTENT", intent.id(),
                "SELF_SERVICE_KIOSK", paymentGateway.provider(), correlationId
        );
        return intent;
    }

    @Transactional
    public PaymentIntentRecord handleWebhook(String payload, String signature, String correlationId) {
        if (!paymentGateway.available()) {
            throw new BusinessException("ONLINE_PAYMENT_PROVIDER_NOT_CONFIGURED", "La pasarela no esta configurada.");
        }
        WebhookResult webhook = paymentGateway.verifyWebhook(payload, signature);
        PaymentIntentRecord intent = checkoutRepository.paymentIntentByProvider(
                paymentGateway.provider(), webhook.providerIntentId()
        ).orElseThrow(() -> new BusinessException("PAYMENT_INTENT_NOT_FOUND", "La intencion de pago no existe."));
        if (webhook.amountCents() != intent.amountCents()
                || !webhook.currency().equalsIgnoreCase(intent.currency())) {
            checkoutRepository.updatePaymentStatus(
                    intent.id(), intent.status(), "FAILED", "Amount or currency mismatch", null
            );
            publishPaymentEvent(intent, "PAYMENT_FAILED", webhook, "amount_or_currency_mismatch");
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH", "El importe confirmado por la pasarela no coincide.");
        }
        String providerStatus = normalizeProviderStatus(webhook.status());
        if ("SUCCEEDED".equals(providerStatus)) {
            completeSuccessfulPayment(intent, webhook, correlationId);
        } else if (Set.of("FAILED", "CANCELLED", "EXPIRED").contains(providerStatus)) {
            checkoutRepository.updatePaymentStatus(
                    intent.id(), intent.status(), providerStatus, providerStatus, webhook.occurredAt()
            );
            publishPaymentEvent(intent, "PAYMENT_FAILED", webhook, providerStatus.toLowerCase());
        }
        return checkoutRepository.paymentIntent(intent.id()).orElseThrow();
    }

    private void completeSuccessfulPayment(
            PaymentIntentRecord intent,
            WebhookResult webhook,
            String correlationId
    ) {
        if ("SUCCEEDED".equals(intent.status())) return;
        QuoteRecord quote = checkoutRepository.quote(intent.quoteId()).orElseThrow();
        if (!checkoutRepository.consumeQuote(quote.id(), OffsetDateTime.now())) {
            throw new BusinessException("SELF_SERVICE_QUOTE_CONSUMED", "La cotizacion ya fue utilizada o expiro.");
        }
        GymOsPrincipal principal = new GymOsPrincipal(
                null, intent.organizationId(), intent.branchId(), "payment-webhook", "Pasarela", Set.of()
        );
        String operationKey = "kiosk-payment:" + intent.id();
        if ("RENEWAL".equals(quote.operationType())) {
            membershipOperations.renew(
                    principal, quote.membershipId(),
                    new RenewMembershipOperationCommand(
                            "CARD_ONLINE", webhook.providerIntentId(), true,
                            "Pago confirmado por webhook", operationKey
                    ), correlationId
            );
        } else if ("PLAN_CHANGE".equals(quote.operationType())) {
            membershipOperations.changePlan(
                    principal, quote.membershipId(),
                    new ChangeMembershipPlanOperationCommand(
                            quote.planId(), "CARD_ONLINE", webhook.providerIntentId(), true,
                            "Pago confirmado por webhook", operationKey
                    ), correlationId
            );
        } else if ("ENROLLMENT".equals(quote.operationType())) {
            enrollmentOperations.enroll(
                    principal,
                    new EnrollMembershipOperationCommand(
                            quote.clientId(), quote.planId(), "CARD_ONLINE", webhook.providerIntentId(), true,
                            "Pago confirmado por webhook", operationKey
                    ), correlationId
            );
        }
        checkoutRepository.updatePaymentStatus(
                intent.id(), intent.status(), "SUCCEEDED", null, webhook.occurredAt()
        );
        publishPaymentEvent(intent, "PAYMENT_COMPLETED", webhook, null);
        auditService.recordSystem(
                intent.organizationId(), intent.branchId(), null,
                "SELF_SERVICE_PAYMENT_COMPLETED", "SELF_SERVICE_PAYMENT_INTENT", intent.id(),
                "PAYMENT_WEBHOOK", webhook.eventId(), correlationId
        );
    }

    private MembershipRecord requireMembership(SessionRecord session, UUID membershipId) {
        if (membershipId == null) {
            throw new BusinessException("SELF_SERVICE_MEMBERSHIP_REQUIRED", "Selecciona una membresia.");
        }
        return selfServiceRepository.memberships(
                session.organizationId(), session.branchId(), session.clientId()
        ).stream().filter(item -> item.id().equals(membershipId)).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SELF_SERVICE_MEMBERSHIP_NOT_FOUND", "La membresia no pertenece al socio verificado."
                ));
    }

    private void publishPaymentEvent(
            PaymentIntentRecord intent,
            String eventType,
            WebhookResult webhook,
            String reason
    ) {
        automationDomainEvents.publish(new DomainEvent(
                intent.organizationId(), intent.branchId(), eventType, webhook.eventId(), 1, webhook.occurredAt(),
                json(Map.of(
                        "paymentIntentId", intent.id().toString(),
                        "providerIntentId", webhook.providerIntentId(),
                        "amountCents", intent.amountCents(),
                        "currency", intent.currency(),
                        "status", eventType,
                        "reason", reason == null ? "" : reason
                ))
        ));
    }

    private String benefitsJson(Quote quote) {
        return json(quote.appliedBenefits());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize checkout data", exception);
        }
    }

    private String normalizeOperation(String value) {
        return required(value, "SELF_SERVICE_OPERATION_REQUIRED").toUpperCase();
    }

    private String normalizeProviderStatus(String value) {
        String status = value == null ? "PENDING" : value.toUpperCase();
        return switch (status) {
            case "CREATED", "PENDING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED" -> status;
            case "APPROVED", "PAID", "COMPLETED" -> "SUCCEEDED";
            case "REJECTED", "DECLINED" -> "FAILED";
            default -> "PENDING";
        };
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, "Falta un dato requerido para continuar.");
        }
        return value.trim();
    }

    private GymOsPrincipal kioskPrincipal(SessionRecord session) {
        return new GymOsPrincipal(
                null, session.organizationId(), session.branchId(),
                "self-service-kiosk", "Kiosco", Set.of()
        );
    }
}
