package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.util.UUID;

final class SalesRecordResolver {
	private SalesRecordResolver() {
	}

	static SaleHeader requireSale(SalesRepository repository, UUID organizationId, UUID saleId) {
		return repository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
	}

	static PaymentDetail requirePayment(SalesRepository repository, UUID organizationId, UUID paymentId) {
		return repository.payment(organizationId, paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	static UUID requireSaleIdForPayment(SalesRepository repository, UUID organizationId, UUID paymentId) {
		return repository.saleIdForPayment(organizationId, paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}
}
