package com.gymos.sales.application;

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
		return SalesRecordResolver.requireSaleForBranch(salesRepository, principal, saleId);
	}

	PaymentDetail payment(GymOsPrincipal principal, UUID paymentId) {
		return SalesRecordResolver.requirePayment(salesRepository, principal.organizationId(), paymentId);
	}

	SaleHeader saleForPayment(GymOsPrincipal principal, UUID paymentId) {
		return SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
	}
}
