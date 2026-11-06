package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;

final class SalesRecordResolver {
	private SalesRecordResolver() {
	}

	static SaleHeader requireSaleForBranch(SalesRepository repository, GymOsPrincipal principal, UUID saleId) {
		SalesAccessGuard.requireBranch(principal);
		return repository.saleHeader(principal.organizationId(), saleId)
				.filter(sale -> principal.branchId().equals(sale.branchId()))
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
	}

	static PaymentDetail requirePayment(SalesRepository repository, UUID organizationId, UUID paymentId) {
		return repository.payment(organizationId, paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	static SaleHeader requirePaymentSaleForBranch(SalesRepository repository, GymOsPrincipal principal, UUID paymentId) {
		SalesAccessGuard.requireBranch(principal);
		return repository.saleIdForPayment(principal.organizationId(), paymentId)
				.flatMap(saleId -> repository.saleHeader(principal.organizationId(), saleId))
				.filter(sale -> principal.branchId().equals(sale.branchId()))
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}
}
