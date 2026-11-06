package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.PaymentRecordedV1;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository.PaymentRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SalesPaymentRecordingService {
	private final SalesRepository salesRepository;
	private final SalesLookupService lookupService;
	private final SalesPaymentCommandRepository commandRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	SalesPaymentRecordingService(SalesRepository salesRepository, SalesLookupService lookupService,
			SalesPaymentCommandRepository commandRepository, AuditService auditService, ApplicationEventPublisher eventPublisher) {
		this.salesRepository = salesRepository;
		this.lookupService = lookupService;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	void record(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		SaleHeader sale = lookupService.saleForBranch(principal, saleId);
		String requestHash = SalesIdempotency.paymentRequestHash(request);
		String idempotencyKey = SalesIdempotency.blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existingSaleId = commandRepository.saleIdByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existingSaleId.isPresent()) {
				if (!existingSaleId.get().equals(saleId)) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otra venta.");
				}
				SalesIdempotency.ensureSameRequest(
						salesRepository.paymentRequestHashByIdempotencyKey(principal.organizationId(), idempotencyKey).orElse(null),
						requestHash);
				return;
			}
		}
		if (SalesIdempotency.blankToNull(request.receiptObjectKey()) != null) {
			throw new BusinessException("PAYMENT_RECEIPT_UPLOAD_REQUIRED", "Adjunta el comprobante al pago mediante la carga de archivos.");
		}
		if (request.amountCents() <= 0) {
			throw new BusinessException("PAYMENT_AMOUNT_INVALID", "El pago debe ser mayor a cero.");
		}
		String method = request.method().trim().toUpperCase();
		String status = request.validateNow() || "CASH".equals(method) ? "COMPLETED" : "PENDING_VALIDATION";
		OffsetDateTime now = OffsetDateTime.now();
		UUID paymentId = UUID.randomUUID();
		commandRepository.create(new PaymentRecord(paymentId, principal.organizationId(), principal.branchId(), saleId, sale.clientId(),
				method, status, request.amountCents(), sale.currency(), SalesIdempotency.blankToNull(request.reference()),
				null, now, "COMPLETED".equals(status) ? now : null,
				"COMPLETED".equals(status) ? principal.userId() : null, SalesIdempotency.blankToNull(request.notes()),
				principal.userId(), idempotencyKey, requestHash));
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "POS", null, correlationId);
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, principal.organizationId(), principal.branchId(),
				saleId, paymentId, method, status, request.amountCents(), sale.currency(), now));
	}
}
