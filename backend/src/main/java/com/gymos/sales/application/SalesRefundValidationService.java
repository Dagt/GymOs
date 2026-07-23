package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundValidationService {
	private final SalesRepository salesRepository;
	private final SalesLookupService lookupService;

	SalesRefundValidationService(SalesRepository salesRepository, SalesLookupService lookupService) {
		this.salesRepository = salesRepository;
		this.lookupService = lookupService;
	}

	void validate(GymOsPrincipal principal, UUID saleId, SaleHeader sale, RequestRefundCommand request) {
		if (request.amountCents() <= 0) {
			throw new BusinessException("REFUND_AMOUNT_INVALID", "El reembolso debe ser mayor a cero.");
		}
		validatePaymentSelection(principal, saleId, request);
		long alreadyRefunded = salesRepository.processedRefundTotal(principal.organizationId(), saleId);
		long refundable = sale.paidCents() - alreadyRefunded;
		if (request.amountCents() > refundable) {
			throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE_BALANCE", "El reembolso supera el saldo pagado disponible.");
		}
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
