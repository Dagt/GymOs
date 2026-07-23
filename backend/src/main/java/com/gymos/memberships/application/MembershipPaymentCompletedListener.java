package com.gymos.memberships.application;

import com.gymos.sales.api.events.PaymentCompletedV1;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipPaymentCompletedListener {
	private final MembershipActivationService membershipActivationService;

	MembershipPaymentCompletedListener(MembershipActivationService membershipActivationService) {
		this.membershipActivationService = membershipActivationService;
	}

	@EventListener
	@Transactional
	public void onPaymentCompleted(PaymentCompletedV1 event) {
		membershipActivationService.processPaymentCompleted(event);
	}
}
