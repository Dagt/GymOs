package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundService {
	private final SalesRepository salesRepository;
	private final SalesLookupService lookupService;
	private final SalesRefundCommandRepository commandRepository;
	private final AuditService auditService;

	SalesRefundService(SalesRepository salesRepository, SalesLookupService lookupService, SalesRefundCommandRepository commandRepository, AuditService auditService) {
		this.salesRepository = salesRepository;
		this.lookupService = lookupService;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
	}

	UUID process(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		SaleHeader sale = lookupService.saleForBranch(principal, saleId);
		if (request.amountCents() <= 0) {
			throw new BusinessException("REFUND_AMOUNT_INVALID", "El reembolso debe ser mayor a cero.");
		}
		String idempotencyKey = SalesIdempotency.blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existing = salesRepository.refundByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existing.isPresent()) {
				var refund = existing.get();
				if (!refund.saleId().equals(saleId)
						|| refund.amountCents() != request.amountCents()
						|| (request.paymentId() != null && !request.paymentId().equals(refund.paymentId()))) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otro reembolso.");
				}
				return refund.saleId();
			}
		}
		validatePaymentSelection(principal, saleId, request);
		long alreadyRefunded = salesRepository.processedRefundTotal(principal.organizationId(), saleId);
		long refundable = sale.paidCents() - alreadyRefunded;
		if (request.amountCents() > refundable) {
			throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE_BALANCE", "El reembolso supera el saldo pagado disponible.");
		}
		UUID refundId = commandRepository.createProcessed(
				principal.organizationId(), principal.branchId(), saleId, request.paymentId(), request.amountCents(),
				sale.currency(), request.reason(), idempotencyKey, principal.userId()
		);
		auditService.record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", request.reason(), correlationId);
		return saleId;
	}

	private void validatePaymentSelection(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (request.paymentId() == null) return;
		var payment = lookupService.payment(principal, request.paymentId());
		UUID paymentSaleId = lookupService.saleIdForPayment(principal, request.paymentId());
		if (!paymentSaleId.equals(saleId)) throw new BusinessException("PAYMENT_SALE_MISMATCH", "El pago no pertenece a la venta.");
		if (!"COMPLETED".equals(payment.status())) throw new BusinessException("PAYMENT_NOT_COMPLETED", "Solo puedes reembolsar pagos completados.");
		if (request.amountCents() > payment.amountCents()) throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "El reembolso no puede superar el pago seleccionado.");
	}
}
