package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import com.gymos.payments.api.events.PointPaymentClosed;
import com.gymos.sales.api.events.PointSaleStockReleased;
import com.gymos.sales.infrastructure.SalesFinancialIntegrityRepository;
import org.springframework.context.ApplicationEventPublisher;
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
	private final SalesFinancialIntegrityRepository integrity;
	private final ApplicationEventPublisher events;

	public ProviderPaymentEventListener(
			SalesRepository salesRepository,
			SalesPaymentCommandRepository commandRepository,
			SalePaymentCompletionService completionService,
			AuditService auditService, SalesFinancialIntegrityRepository integrity, ApplicationEventPublisher events
	) {
		this.salesRepository = salesRepository;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.auditService = auditService;
		this.integrity = integrity;
		this.events = events;
	}

	@EventListener
	@Transactional
	public void onPointPaymentClosed(PointPaymentClosed event) {
		var sale = salesRepository.saleHeader(event.organizationId(), event.saleId())
				.filter(candidate -> candidate.branchId().equals(event.branchId()))
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "No se encontró la venta de Point."));
		salesRepository.lockSaleForRefund(event.organizationId(), sale.id());
		var payment = salesRepository.payment(event.organizationId(), event.paymentId())
				.filter(candidate -> "CARD_MERCADO_PAGO".equals(candidate.method()))
				.filter(candidate -> salesRepository.saleIdForPayment(event.organizationId(), candidate.id())
						.filter(sale.id()::equals).isPresent())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "No se encontró el pago de Point."));
		if (!"PENDING_VALIDATION".equals(payment.status())) return;
		commandRepository.reject(event.organizationId(), payment.id(), event.status(), null, OffsetDateTime.now());
		if (!integrity.hasRemainingPointExposure(event.organizationId(), sale.id())) {
			events.publishEvent(new PointSaleStockReleased(event.organizationId(), sale.branchId(), sale.id()));
		}
		auditService.recordSystem(event.organizationId(), sale.branchId(), null, "POINT_PAYMENT_CLOSED",
				"PAYMENT", payment.id(), "PAYMENT_WEBHOOK", event.status(), payment.id().toString());
	}

	@EventListener
	@Transactional
	public void onProviderPaymentCompleted(ProviderPaymentCompletedV1 event) {
		if (event.organizationId() == null || event.saleId() == null || event.paymentId() == null) {
			throw new BusinessException("PROVIDER_PAYMENT_CONTEXT_INVALID", "La confirmacion del proveedor no contiene venta y pago.");
		}
		var scoped = salesRepository.saleHeader(event.organizationId(), event.saleId())
				.filter(sale -> event.branchId() != null && event.branchId().equals(sale.branchId()))
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta confirmada por el proveedor no existe."));
		salesRepository.lockSaleForRefund(event.organizationId(), scoped.id());
		var sale = salesRepository.saleHeader(event.organizationId(), scoped.id()).orElseThrow();
		var payment = salesRepository.payment(event.organizationId(), event.paymentId())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago confirmado por el proveedor no existe."));
		var paymentSaleId = salesRepository.saleIdForPayment(event.organizationId(), event.paymentId()).orElse(null);
		if (!event.saleId().equals(paymentSaleId) || payment.amountCents() != event.amountCents()
				|| !payment.currency().equalsIgnoreCase(event.currency())) {
			throw new BusinessException("PROVIDER_PAYMENT_CONTEXT_MISMATCH", "La confirmacion del proveedor no coincide con el pago registrado.");
		}
		String expectedMethod = switch (event.provider()) {
			case "STRIPE" -> "CARD_ONLINE";
			case "MERCADO_PAGO" -> "CARD_MERCADO_PAGO";
			default -> throw new BusinessException("PROVIDER_PAYMENT_METHOD_MISMATCH", "Proveedor de pago no reconocido.");
		};
		if (!expectedMethod.equals(payment.method())) {
			throw new BusinessException("PROVIDER_PAYMENT_METHOD_MISMATCH", "El proveedor no coincide con el metodo de pago registrado.");
		}
		if ("COMPLETED".equals(payment.status())) return;
		if ("CANCELLED".equals(sale.status()) || payment.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PROVIDER_PAYMENT_REQUIRES_REVIEW", "El pago conciliado no puede aplicarse al saldo de la venta.");
		}
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
