package com.gymos.memberships.application;

import com.gymos.memberships.api.events.MembershipActivatedV1;
import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class MembershipActivationEventPublisher {
	private final ApplicationEventPublisher eventPublisher;

	MembershipActivationEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	void publish(PaymentCompletedV1 paymentEvent, UUID sourceMembershipId, MembershipSummary membership, String action) {
		if ("MEMBERSHIP_RENEWED".equals(action)) {
			eventPublisher.publishEvent(new MembershipRenewedV1(
					paymentEvent.eventKey() + ":membership-renewed",
					paymentEvent.organizationId(),
					paymentEvent.branchId(),
					paymentEvent.clientId(),
					sourceMembershipId,
					membership.id(),
					membership.planId(),
					paymentEvent.saleId(),
					membership.startsOn(),
					membership.endsOn(),
					OffsetDateTime.now()
			));
			return;
		}
		if ("MEMBERSHIP_PLAN_CHANGED".equals(action)) {
			eventPublisher.publishEvent(new MembershipPlanChangedV1(
					paymentEvent.eventKey() + ":membership-plan-changed",
					paymentEvent.organizationId(),
					paymentEvent.branchId(),
					paymentEvent.clientId(),
					sourceMembershipId,
					membership.id(),
					membership.planId(),
					paymentEvent.saleId(),
					membership.startsOn(),
					membership.endsOn(),
					OffsetDateTime.now()
			));
			return;
		}
		eventPublisher.publishEvent(new MembershipActivatedV1(
				paymentEvent.eventKey() + ":membership-activated:" + membership.planId(),
				paymentEvent.organizationId(),
				paymentEvent.branchId(),
				paymentEvent.clientId(),
				membership.id(),
				membership.planId(),
				paymentEvent.saleId(),
				membership.startsOn(),
				membership.endsOn(),
				OffsetDateTime.now()
		));
	}
}
