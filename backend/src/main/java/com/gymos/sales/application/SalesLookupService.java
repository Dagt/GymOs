package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesLookupService {
	private final SalesRepository salesRepository;

	SalesLookupService(SalesRepository salesRepository) {
		this.salesRepository = salesRepository;
	}

	SaleHeader saleForBranch(GymOsPrincipal principal, UUID saleId) {
		return salesRepository.saleHeader(principal.organizationId(), saleId)
				.filter(header -> principal.branchId().equals(header.branchId()))
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
	}

	PaymentDetail payment(GymOsPrincipal principal, UUID paymentId) {
		return salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	SaleHeader saleForPayment(GymOsPrincipal principal, UUID paymentId) {
		return SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
	}
}
