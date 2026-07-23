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
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!sale.branchId().equals(principal.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
		return sale;
	}

	PaymentDetail payment(GymOsPrincipal principal, UUID paymentId) {
		return salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	UUID saleIdForPayment(GymOsPrincipal principal, UUID paymentId) {
		return salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}
}
