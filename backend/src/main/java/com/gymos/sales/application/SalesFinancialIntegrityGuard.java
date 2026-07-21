package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesFinancialIntegrityRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
class SalesFinancialIntegrityGuard {
	private final SalesRepository salesRepository;
	private final SalesFinancialIntegrityRepository integrityRepository;

	SalesFinancialIntegrityGuard(
			SalesRepository salesRepository,
			SalesFinancialIntegrityRepository integrityRepository
	) {
		this.salesRepository = salesRepository;
		this.integrityRepository = integrityRepository;
	}

	@Before("execution(* com.gymos.sales.application.SalesService.addPayment(..)) && args(principal,saleId,request,..)")
	void guardAddedPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request) {
		if (principal == null || request == null) {
			return;
		}
		String key = normalizedKey(request.idempotencyKey());
		if (key != null && integrityRepository.paymentSaleByIdempotencyKey(principal.organizationId(), key).isPresent()) {
			return;
		}
		var sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (request.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.validatePayment(..)) && args(principal,paymentId,..)")
	void guardValidatedPayment(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null) {
			return;
		}
		var payment = salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		if ("COMPLETED".equals(payment.status())) {
			return;
		}
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		var sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (payment.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.requestRefund(..)) && args(principal,saleId,request,..)")
	void guardRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (principal == null || request == null || request.paymentId() == null) {
			return;
		}
		String key = normalizedKey(request.idempotencyKey());
		if (key != null && salesRepository.refundByIdempotencyKey(principal.organizationId(), key).isPresent()) {
			return;
		}
		var payment = salesRepository.payment(principal.organizationId(), request.paymentId())
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		long refunded = integrityRepository.processedRefundTotalForPayment(principal.organizationId(), request.paymentId());
		if (refunded + request.amountCents() > payment.amountCents()) {
			throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "Los reembolsos acumulados no pueden superar el pago seleccionado.");
		}
	}

	private String normalizedKey(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
