package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.MercadoPagoPointOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalesProviderPaymentService {
	private final PointSalePreparation preparation;
	private final MercadoPagoPointOperations mercadoPagoPointOperations;

	SalesProviderPaymentService(PointSalePreparation preparation, MercadoPagoPointOperations mercadoPagoPointOperations) {
		this.preparation = preparation;
		this.mercadoPagoPointOperations = mercadoPagoPointOperations;
	}

	public MercadoPagoPointOperations.PointOrderResult createMercadoPagoOrder(GymOsPrincipal principal, UUID paymentId) {
		return mercadoPagoPointOperations.createOrder(preparation.prepare(principal, paymentId));
	}
}
