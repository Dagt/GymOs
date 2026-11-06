package com.gymos.sales.application;

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
		SalesRecordResolver.requirePaymentSaleForBranch(salesRepository, principal, paymentId);
	}
}
