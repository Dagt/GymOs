package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.infrastructure.SalesRepository;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
class SalesPaymentBranchGuard {
	private final SalesRepository salesRepository;

	SalesPaymentBranchGuard(SalesRepository salesRepository) {
		this.salesRepository = salesRepository;
	}

	@Before("execution(* com.gymos.sales.application.SalesService.validatePayment(..)) && args(principal,paymentId,..)")
	void guardValidation(GymOsPrincipal principal, UUID paymentId) {
		ensurePaymentBelongsToActiveBranch(principal, paymentId);
	}

	@Before("execution(* com.gymos.sales.application.SalesService.rejectPayment(..)) && args(principal,paymentId,..)")
	void guardRejection(GymOsPrincipal principal, UUID paymentId) {
		ensurePaymentBelongsToActiveBranch(principal, paymentId);
	}

	private void ensurePaymentBelongsToActiveBranch(GymOsPrincipal principal, UUID paymentId) {
		if (principal == null || principal.branchId() == null) {
			return;
		}
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		var sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!principal.branchId().equals(sale.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
	}
}
