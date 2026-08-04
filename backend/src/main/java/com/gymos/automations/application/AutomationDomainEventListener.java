package com.gymos.automations.application;

import com.gymos.bookings.api.events.BookingCreatedV1;
import com.gymos.bookings.api.events.BookingStatusChangedV1;
import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.memberships.api.events.MembershipExpiringV1;
import com.gymos.payments.api.events.RecurringChargeDueV1;
import com.gymos.payments.api.events.RecurringChargeFailedV1;
import com.gymos.payments.api.events.RecurringChargeSucceededV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.api.events.PaymentRecordedV1;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AutomationDomainEventListener {
	private final AutomationService automationService;

	public AutomationDomainEventListener(AutomationService automationService) {
		this.automationService = automationService;
	}

	@ApplicationModuleListener
	public void onPaymentCompleted(PaymentCompletedV1 event) {
		String eventReference = reference(event.eventKey(), "payment-completed:" + event.saleId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "PAYMENT_COMPLETED",
				eventReference,
				payload(
						"occurrenceKey", eventReference,
						"saleId", event.saleId(),
						"clientId", event.clientId(),
						"businessContext", event.businessContext()
				)
		);
	}

	@ApplicationModuleListener
	public void onPaymentRecorded(PaymentRecordedV1 event) {
		if (!"PENDING_VALIDATION".equals(event.status())) return;
		String eventReference = reference(event.eventKey(), "payment-pending:" + event.paymentId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "PAYMENT_PENDING_VALIDATION",
				eventReference,
				payload(
						"occurrenceKey", eventReference,
						"saleId", event.saleId(),
						"paymentId", event.paymentId(),
						"method", event.method(),
						"amountCents", event.amountCents()
				)
		);
	}

	@ApplicationModuleListener
	public void onBookingCreated(BookingCreatedV1 event) {
		String eventReference = reference(event.eventKey(), "booking-created:" + event.bookingId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "BOOKING_CREATED",
				eventReference,
				payload(
						"occurrenceKey", eventReference,
						"bookingId", event.bookingId(),
						"clientId", event.clientId(),
						"resourceId", event.resourceId()
				)
		);
	}

	@ApplicationModuleListener
	public void onBookingStatusChanged(BookingStatusChangedV1 event) {
		String triggerType = switch (event.status()) {
			case "CONFIRMED" -> "BOOKING_CONFIRMED";
			case "CANCELLED" -> "BOOKING_CANCELLED";
			default -> null;
		};
		if (triggerType == null) return;
		String eventReference = reference(
				event.eventKey(), "booking-status:" + event.bookingId() + ":" + event.status()
		);
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), triggerType,
				eventReference,
				payload(
						"occurrenceKey", eventReference,
						"bookingId", event.bookingId(),
						"clientId", event.clientId(),
						"resourceId", event.resourceId(),
						"status", event.status()
				)
		);
	}

	@ApplicationModuleListener
	public void onMembershipExpiring(MembershipExpiringV1 event) {
		String eventReference = reference(
				event.eventKey(), "membership-expiring:" + event.membershipId() + ":" + event.businessDate()
		);
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "MEMBERSHIP_EXPIRING", eventReference,
				payload(
						"occurrenceKey", "membership-expiring:" + event.membershipId() + ":" + event.endsOn(),
						"membershipId", event.membershipId(),
						"clientId", event.clientId(),
						"planId", event.planId(),
						"endsOn", event.endsOn(),
						"graceEndsOn", event.graceEndsOn(),
						"businessDate", event.businessDate(),
						"daysUntilEnd", event.daysUntilEnd()
				)
		);
	}

	@ApplicationModuleListener
	public void onMembershipExpired(MembershipExpiredV1 event) {
		String eventReference = reference(
				event.eventKey(), "membership-expired:" + event.membershipId() + ":" + event.graceEndsOn()
		);
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "MEMBERSHIP_EXPIRED", eventReference,
				payload(
						"occurrenceKey", eventReference,
						"membershipId", event.membershipId(),
						"clientId", event.clientId(),
						"planId", event.planId(),
						"endsOn", event.endsOn(),
						"graceEndsOn", event.graceEndsOn(),
						"expiredOn", event.expiredOn()
				)
		);
	}

	@ApplicationModuleListener
	public void onRecurringChargeDue(RecurringChargeDueV1 event) {
		String eventReference = reference(event.eventKey(), "recurring-charge-due:" + event.chargeId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "RECURRING_CHARGE_DUE", eventReference,
				payload(
						"occurrenceKey", eventReference,
						"chargeId", event.chargeId(),
						"mandateId", event.mandateId(),
						"membershipId", event.membershipId(),
						"clientId", event.clientId(),
						"planId", event.planId(),
						"dueOn", event.dueOn(),
						"attemptNumber", event.attemptNumber()
				)
		);
	}

	@ApplicationModuleListener
	public void onRecurringChargeSucceeded(RecurringChargeSucceededV1 event) {
		String eventReference = reference(event.eventKey(), "recurring-charge-succeeded:" + event.chargeId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "RECURRING_CHARGE_SUCCEEDED", eventReference,
				payload(
						"occurrenceKey", eventReference,
						"chargeId", event.chargeId(),
						"mandateId", event.mandateId(),
						"membershipId", event.membershipId(),
						"clientId", event.clientId(),
						"planId", event.planId(),
						"saleId", event.saleId(),
						"amountCents", event.amountCents(),
						"currency", event.currency(),
						"dueOn", event.dueOn()
				)
		);
	}

	@ApplicationModuleListener
	public void onRecurringChargeFailed(RecurringChargeFailedV1 event) {
		String eventReference = reference(event.eventKey(), "recurring-charge-failed:" + event.chargeId());
		automationService.enqueueDomainEvent(
				event.organizationId(), event.branchId(), "RECURRING_CHARGE_FAILED", eventReference,
				payload(
						"occurrenceKey", eventReference,
						"chargeId", event.chargeId(),
						"mandateId", event.mandateId(),
						"membershipId", event.membershipId(),
						"clientId", event.clientId(),
						"planId", event.planId(),
						"saleId", event.saleId(),
						"dueOn", event.dueOn(),
						"attemptNumber", event.attemptNumber(),
						"exhausted", event.exhausted(),
						"errorCode", event.errorCode()
				)
		);
	}

	private String reference(String eventKey, String fallback) {
		return eventKey == null || eventKey.isBlank() ? fallback : eventKey;
	}

	private Map<String, Object> payload(Object... entries) {
		Map<String, Object> payload = new LinkedHashMap<>();
		for (int index = 0; index < entries.length; index += 2) {
			Object value = entries[index + 1];
			if (value != null) {
				payload.put(entries[index].toString(), value);
			}
		}
		return payload;
	}
}
