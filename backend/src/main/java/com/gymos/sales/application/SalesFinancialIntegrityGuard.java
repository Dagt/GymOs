package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.sales.application.command.CreateSaleCommand;
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
	private final PaymentConfigurationOperations paymentConfigurationService;

	SalesFinancialIntegrityGuard(
			SalesRepository salesRepository,
			SalesFinancialIntegrityRepository integrityRepository,
			PaymentConfigurationOperations paymentConfigurationService
	) {
		this.salesRepository = salesRepository;
		this.integrityRepository = integrityRepository;
		this.paymentConfigurationService = paymentConfigurationService;
	}

	@Before("execution(* com.gymos.sales.application.SalesService.create(..)) && args(principal,request,..)")
	void guardInitialPayment(GymOsPrincipal principal, CreateSaleCommand request) {
		if (principal != null && request != null && request.payment() != null) {
			paymentConfigurationService.requireReceptionMethod(principal.organizationId(), principal.branchId(), request.payment().method());
			requireProviderPending(request.payment());
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.addPayment(..)) && args(principal,saleId,request,..)")
	void guardAddedPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request) {
		if (principal == null || request == null) {
			return;
		}
		var sale = SalesRecordResolver.requireSaleForBranch(salesRepository, principal, saleId);
		paymentConfigurationService.requireReceptionMethod(principal.organizationId(), principal.branchId(), request.method());
		requireProviderPending(request);
		String key = normalizedKey(request.idempotencyKey());
		if (key != null && integrityRepository.paymentSaleByIdempotencyKey(principal.organizationId(), key).isPresent()) {
			return;
		}
		if (request.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.validatePayment(..)) && args(principal,paymentId,..)")
	void guardValidatedPayment(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null) {
			return;
		}
		var sale = SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
		var payment = SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
		if ("CARD_MERCADO_PAGO".equals(payment.method()) || "CARD_ONLINE".equals(payment.method())) {
			throw new BusinessException("PROVIDER_PAYMENT_MANUAL_VALIDATION_FORBIDDEN", "Este pago solo puede confirmarse mediante el webhook firmado del proveedor.");
		}
		if ("COMPLETED".equals(payment.status())) {
			return;
		}
		if (payment.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.rejectPayment(..)) && args(principal,paymentId,..)")
	void guardProviderPaymentRejection(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null) return;
		SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
		var payment = SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
		if ("CARD_MERCADO_PAGO".equals(payment.method()) || "CARD_ONLINE".equals(payment.method())) {
			throw new BusinessException(
					"PROVIDER_PAYMENT_MANUAL_REJECTION_FORBIDDEN",
					"El estado final de este pago debe conciliarse con el proveedor; no puede rechazarse manualmente."
			);
		}
	}

	@Before("execution(* com.gymos.sales.application.SalesService.requestRefund(..)) && args(principal,saleId,request,..)")
	void guardRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (principal == null || request == null || request.paymentId() == null) {
			return;
		}
		SalesRecordResolver.requireSaleForBranch(salesRepository, principal, saleId);
		String key = normalizedKey(request.idempotencyKey());
		if (key != null && salesRepository.refundByIdempotencyKey(principal.organizationId(), key).isPresent()) {
			return;
		}
		var paymentSale = SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, request.paymentId());
		if (!saleId.equals(paymentSale.id())) {
			throw new BusinessException("PAYMENT_SALE_MISMATCH", "El pago no pertenece a la venta.");
		}
		var payment = SalesRecordResolver.requirePayment(
				salesRepository,
				principal.organizationId(),
				request.paymentId()
		);
		long refunded = integrityRepository.processedRefundTotalForPayment(principal.organizationId(), request.paymentId());
		if (refunded + request.amountCents() > payment.amountCents()) {
			throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "Los reembolsos acumulados no pueden superar el pago seleccionado.");
		}
	}

	private String normalizedKey(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireProviderPending(ManualPaymentCommand payment) {
		if (payment.validateNow() && ("CARD_MERCADO_PAGO".equalsIgnoreCase(payment.method())
				|| "CARD_ONLINE".equalsIgnoreCase(payment.method()))) {
			throw new BusinessException("PROVIDER_PAYMENT_CANNOT_VALIDATE_NOW", "El pago integrado debe quedar pendiente hasta recibir la confirmacion firmada del proveedor.");
		}
	}
}
