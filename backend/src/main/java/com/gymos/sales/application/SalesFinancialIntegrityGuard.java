package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesFinancialIntegrityRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

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

	void guardAddedPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request) {
		if (principal == null || request == null) {
			return;
		}
		var sale = lockSale(principal, saleId);
		String key = normalizedKey(request.idempotencyKey());
		if (key != null) {
			salesRepository.lockIdempotency(principal.organizationId(), "payment", key);
			if (integrityRepository.paymentSaleByIdempotencyKey(principal.organizationId(), key).isPresent()) return;
		}
		requireOpenSale(sale.status());
		paymentConfigurationService.requireReceptionMethod(principal.organizationId(), principal.branchId(), request.method());
		requireProviderPending(request);
		if (request.amountCents() > sale.balanceCents() - integrityRepository.pendingPaymentTotal(principal.organizationId(), saleId)) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	void guardValidatedPayment(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null) {
			return;
		}
		var authorized = SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
		salesRepository.lockSaleForRefund(principal.organizationId(), authorized.id());
		var sale = SalesRecordResolver.requireSaleForBranch(salesRepository, principal, authorized.id());
		var payment = SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
		if ("CARD_MERCADO_PAGO".equals(payment.method()) || "CARD_ONLINE".equals(payment.method())) {
			throw new BusinessException("PROVIDER_PAYMENT_MANUAL_VALIDATION_FORBIDDEN", "Este pago solo puede confirmarse mediante el webhook firmado del proveedor.");
		}
		if ("COMPLETED".equals(payment.status())) {
			return;
		}
		requireOpenSale(sale.status());
		if ("REJECTED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_REJECTED", "No puedes validar un pago rechazado.");
		}
		if (payment.amountCents() > sale.balanceCents()) {
			throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "El pago no puede superar el saldo pendiente de la venta.");
		}
	}

	void guardProviderPaymentRejection(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null) return;
		var sale = SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
		salesRepository.lockSaleForRefund(principal.organizationId(), sale.id());
		var payment = SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
		if ("CARD_ONLINE".equals(payment.method()) || ("CARD_MERCADO_PAGO".equals(payment.method())
				&& integrityRepository.providerSubmissionMayHaveStarted(principal.organizationId(), paymentId))) {
			throw new BusinessException(
					"PROVIDER_PAYMENT_MANUAL_REJECTION_FORBIDDEN",
					"El estado final de este pago debe conciliarse con el proveedor; no puede rechazarse manualmente."
			);
		}
	}

	void guardRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (principal == null || request == null) {
			return;
		}
		if (request.paymentId() == null) {
			if (integrityRepository.hasCompletedProviderPayment(principal.organizationId(), saleId)) {
				throw providerRefundRequired();
			}
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
		if ("CARD_ONLINE".equals(payment.method()) || "CARD_MERCADO_PAGO".equals(payment.method())) {
			throw providerRefundRequired();
		}
		long refunded = integrityRepository.processedRefundTotalForPayment(principal.organizationId(), request.paymentId());
		if (request.amountCents() > payment.amountCents() - refunded) {
			throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "Los reembolsos acumulados no pueden superar el pago seleccionado.");
		}
	}

	com.gymos.sales.model.SaleHeader lockSale(GymOsPrincipal principal, UUID saleId) {
		SalesRecordResolver.requireSaleForBranch(salesRepository, principal, saleId);
		salesRepository.lockSaleForRefund(principal.organizationId(), saleId);
		return SalesRecordResolver.requireSaleForBranch(salesRepository, principal, saleId);
	}

	private BusinessException providerRefundRequired() {
		return new BusinessException("PROVIDER_REFUND_REQUIRES_RECONCILIATION",
				"Los pagos integrados deben reembolsarse y conciliarse con el proveedor.");
	}

	private void requireOpenSale(String status) {
		if ("CANCELLED".equals(status)) {
			throw new BusinessException("SALE_CANCELLED", "La venta está cancelada.");
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
