package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundService {
	private final SalesRepository salesRepository;
	private final AuditService auditService;

	SalesRefundService(SalesRepository salesRepository, AuditService auditService) {
		this.salesRepository = salesRepository;
		this.auditService = auditService;
	}

	UUID process(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		SaleHeader sale = ensureSale(principal, saleId);
		if (request.amountCents() <= 0) {
			throw new BusinessException("REFUND_AMOUNT_INVALID", "El reembolso debe ser mayor a cero.");
		}
		String idempotencyKey = blankToNull(request.idempotencyKey());
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
		UUID refundId = salesRepository.createProcessedRefund(
				principal.organizationId(),
				principal.branchId(),
				saleId,
				request.paymentId(),
				request.amountCents(),
				sale.currency(),
				request.reason(),
				idempotencyKey,
				principal.userId()
		);
		auditService.record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", request.reason(), correlationId);
		return saleId;
	}

	private void validatePaymentSelection(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (request.paymentId() == null) {
			return;
		}
		PaymentDetail payment = salesRepository.payment(principal.organizationId(), request.paymentId())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		UUID paymentSaleId = salesRepository.saleIdForPayment(principal.organizationId(), request.paymentId())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		if (!paymentSaleId.equals(saleId)) {
			throw new BusinessException("PAYMENT_SALE_MISMATCH", "El pago no pertenece a la venta.");
		}
		if (!"COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_NOT_COMPLETED", "Solo puedes reembolsar pagos completados.");
		}
		if (request.amountCents() > payment.amountCents()) {
			throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "El reembolso no puede superar el pago seleccionado.");
		}
	}

	private SaleHeader ensureSale(GymOsPrincipal principal, UUID saleId) {
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!sale.branchId().equals(principal.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
		return sale;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
