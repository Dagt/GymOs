package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import java.time.OffsetDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProviderPaymentEventListener {
	private final SalesRepository salesRepository;
	private final SalesPaymentCommandRepository commandRepository;
	private final SalePaymentCompletionService completionService;
	private final AuditService auditService;

	public ProviderPaymentEventListener(
			SalesRepository salesRepository,
			SalesPaymentCommandRepository commandRepository,
			SalePaymentCompletionService completionService,
			AuditService auditService
	) {
		this.salesRepository = salesRepository;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.auditService = auditService;
	}

	@EventListener
	@Transactional
	public void onProviderPaymentCompleted(ProviderPaymentCompletedV1 event) {
		if (event.organizationId() == null || event.saleId() == null || event.paymentId() == null) {
			throw new BusinessException("PROVIDER_PAYMENT_CONTEXT_INVALID", "La confirmacion del proveedor no contiene venta y pago.");
		}
		var sale = salesRepository.saleHeader(event.organizationId(), event.saleId())
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta confirmada por el proveedor no existe."));
		var payment = salesRepository.payment(event.organizationId(), event.paymentId())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago confirmado por el proveedor no existe."));
		var paymentSaleId = salesRepository.saleIdForPayment(event.organizationId(), event.paymentId()).orElse(null);
		if (!event.saleId().equals(paymentSaleId) || payment.amountCents() != event.amountCents()
				|| !payment.currency().equalsIgnoreCase(event.currency())
				|| (event.branchId() != null && !event.branchId().equals(sale.branchId()))) {
			throw new BusinessException("PROVIDER_PAYMENT_CONTEXT_MISMATCH", "La confirmacion del proveedor no coincide con el pago registrado.");
		}
		String expectedMethod = "STRIPE".equals(event.provider()) ? "CARD_ONLINE" : "CARD_MERCADO_PAGO";
		if (!expectedMethod.equals(payment.method())) {
			throw new BusinessException("PROVIDER_PAYMENT_METHOD_MISMATCH", "El proveedor no coincide con el metodo de pago registrado.");
		}
		if ("COMPLETED".equals(payment.status())) return;
		if ("REJECTED".equals(payment.status())) {
			throw new BusinessException("PROVIDER_PAYMENT_ALREADY_REJECTED", "El pago fue rechazado antes de la confirmacion del proveedor.");
		}
		OffsetDateTime completedAt = event.occurredAt() == null ? OffsetDateTime.now() : event.occurredAt();
		commandRepository.complete(event.organizationId(), event.paymentId(), null, completedAt);
		auditService.recordSystem(event.organizationId(), sale.branchId(), null, "PROVIDER_PAYMENT_CONFIRMED", "PAYMENT",
				event.paymentId(), "PAYMENT_WEBHOOK", event.provider() + ":" + event.externalOperationId(), event.eventKey());
		completionService.recalculate(event.organizationId(), event.saleId(), null);
	}
}
