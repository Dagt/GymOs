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
		var payment = salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta del pago no existe."));
		var sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!principal.branchId().equals(sale.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
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
				principal.organizationId(), sale.branchId(), saleId, paymentId, sale.clientId(),
				payment.amountCents(), payment.currency(), "mp-point:" + paymentId
		));
	}
}
