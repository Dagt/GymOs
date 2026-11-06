package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.MercadoPagoPointOperations;
import com.gymos.sales.infrastructure.SalesRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalesProviderPaymentService {
	private final SalesRepository salesRepository;
	private final MercadoPagoPointOperations mercadoPagoPointOperations;

	public SalesProviderPaymentService(
			SalesRepository salesRepository,
			MercadoPagoPointOperations mercadoPagoPointOperations
	) {
		this.salesRepository = salesRepository;
		this.mercadoPagoPointOperations = mercadoPagoPointOperations;
	}

	public MercadoPagoPointOperations.PointOrderResult createMercadoPagoOrder(
			GymOsPrincipal principal,
			UUID paymentId
	) {
		SalesAccessGuard.requireBranch(principal);
		var sale = SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
		var payment = SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
		if (!"CARD_MERCADO_PAGO".equals(payment.method())) {
			throw new BusinessException("MERCADO_PAGO_PAYMENT_METHOD_REQUIRED", "El pago no fue registrado como Mercado Pago Point.");
		}
		if ("COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_COMPLETED", "El pago ya fue confirmado.");
		}
		if ("REJECTED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_REJECTED", "El pago ya fue rechazado.");
		}
		return mercadoPagoPointOperations.createOrder(new MercadoPagoPointOperations.CreatePointOrderCommand(
				principal.organizationId(), sale.branchId(), sale.id(), paymentId, sale.clientId(),
				payment.amountCents(), payment.currency(), "mp-point:" + paymentId
		));
	}
}
