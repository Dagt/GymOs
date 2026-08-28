package com.gymos.visitpackages.application;

import com.gymos.sales.api.events.PaymentCompletedV1;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class VisitPackagePaymentCompletedListener {
	private final VisitPackageActivationService activationService;

	VisitPackagePaymentCompletedListener(VisitPackageActivationService activationService) {
		this.activationService = activationService;
	}

	@EventListener
	public void onPaymentCompleted(PaymentCompletedV1 event) {
		if (!"VISIT_PACKAGE_PURCHASE".equals(event.businessContext())) return;
		activationService.activate(
				event.organizationId(), event.branchId(), event.saleId(), event.occurredAt(), event.eventKey());
	}
}
