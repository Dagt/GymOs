package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.PaymentRejectedV1;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SalesPaymentLifecycleService {
	private final SalesLookupService lookupService;
	private final SalesPaymentCommandRepository commandRepository;
	private final SalePaymentCompletionService completionService;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	SalesPaymentLifecycleService(SalesLookupService lookupService, SalesPaymentCommandRepository commandRepository,
			SalePaymentCompletionService completionService, AuditService auditService, ApplicationEventPublisher eventPublisher) {
		this.lookupService = lookupService;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	UUID validate(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		UUID saleId = lookupService.saleForPayment(principal, paymentId).id();
		var payment = lookupService.payment(principal, paymentId);
		if ("COMPLETED".equals(payment.status())) return saleId;
		if ("REJECTED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_REJECTED", "No puedes validar un pago rechazado.");
		}
		commandRepository.complete(principal.organizationId(), paymentId, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "PAYMENT_VALIDATED", "PAYMENT", paymentId, "POS", null, correlationId);
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return saleId;
	}

	UUID reject(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		UUID saleId = lookupService.saleForPayment(principal, paymentId).id();
		var payment = lookupService.payment(principal, paymentId);
		if ("COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_COMPLETED", "No puedes rechazar un pago completado.");
		}
		if ("REJECTED".equals(payment.status())) return saleId;
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.reject(principal.organizationId(), paymentId, SalesIdempotency.blankToNull(reason), principal.userId(), now);
		auditService.record(principal, "PAYMENT_REJECTED", "PAYMENT", paymentId, "POS", reason, correlationId);
		eventPublisher.publishEvent(new PaymentRejectedV1(
				"payment-rejected:" + paymentId,
				principal.organizationId(),
				principal.branchId(),
				saleId,
				paymentId,
				reason,
				now
		));
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return saleId;
	}
}
