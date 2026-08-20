package com.gymos.automations.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gymos.bookings.api.events.BookingStatusChangedV1;
import com.gymos.memberships.api.events.MembershipExpiringV1;
import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.payments.api.events.RecurringChargeFailedV1;
import com.gymos.sales.api.events.PaymentRecordedV1;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationDomainEventListenerTests {
	private final AutomationService service = mock(AutomationService.class);
	private final AutomationDomainEventListener listener = new AutomationDomainEventListener(service);

	@Test
	void onlyPendingValidationPaymentsEnterTheAutomationInbox() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		listener.onPaymentRecorded(new PaymentRecordedV1(
				"payment-recorded:" + paymentId, organizationId, branchId, saleId, paymentId,
				"TRANSFER", "PENDING_VALIDATION", 10000, "MXN", OffsetDateTime.now()
		));

		verify(service).enqueueDomainEvent(
				eq(organizationId), eq(branchId), eq("PAYMENT_PENDING_VALIDATION"),
				eq("payment-recorded:" + paymentId),
				argThat(payload -> payload.get("method").equals("TRANSFER")
						&& payload.get("amountCents").equals(10000L))
		);
	}

	@Test
	void completedCashPaymentDoesNotCreatePendingValidationOccurrence() {
		listener.onPaymentRecorded(new PaymentRecordedV1(
				"payment-recorded:cash", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"CASH", "COMPLETED", 10000, "MXN", OffsetDateTime.now()
		));

		verify(service, never()).enqueueDomainEvent(
				any(), any(), anyString(), anyString(), anyMap()
		);
	}

	@Test
	void confirmedBookingEntersTheMatchingDurableTrigger() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID bookingId = UUID.randomUUID();
		String eventKey = "booking-status:" + bookingId + ":CONFIRMED";
		OffsetDateTime now = OffsetDateTime.now();

		listener.onBookingStatusChanged(new BookingStatusChangedV1(
				eventKey, organizationId, branchId, bookingId, UUID.randomUUID(), UUID.randomUUID(),
				"RESERVED", "CONFIRMED", now.plusDays(1), now.plusDays(1).plusHours(1), now
		));

		verify(service).enqueueDomainEvent(
				eq(organizationId), eq(branchId), eq("BOOKING_CONFIRMED"), eq(eventKey), anyMap()
		);
	}

	@Test
	void checkedInBookingDoesNotCreateAnUnsupportedOccurrence() {
		OffsetDateTime now = OffsetDateTime.now();
		listener.onBookingStatusChanged(new BookingStatusChangedV1(
				"booking-status:checked-in", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", "CHECKED_IN",
				now.plusDays(1), now.plusDays(1).plusHours(1), now
		));

		verify(service, never()).enqueueDomainEvent(
				any(), any(), anyString(), anyString(), anyMap()
		);
	}

	@Test
	void expiringMembershipUsesAStableOccurrenceAcrossDailyScans() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 3);
		LocalDate endsOn = businessDate.plusDays(3);

		listener.onMembershipExpiring(new MembershipExpiringV1(
				"membership-expiring:" + membershipId + ":" + businessDate,
				organizationId, branchId, UUID.randomUUID(), membershipId, UUID.randomUUID(),
				endsOn, endsOn.plusDays(2), businessDate, 3, OffsetDateTime.now()
		));

		verify(service).enqueueDomainEvent(
				eq(organizationId), eq(branchId), eq("MEMBERSHIP_EXPIRING"),
				eq("membership-expiring:" + membershipId + ":" + businessDate),
				argThat(payload -> payload.get("occurrenceKey").equals(
						"membership-expiring:" + membershipId + ":" + endsOn
				) && payload.get("daysUntilEnd").equals(3))
		);
	}

	@Test
	void expiredMembershipEntersTheDurableTrigger() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		LocalDate endsOn = LocalDate.of(2026, 7, 31);
		LocalDate graceEndsOn = endsOn.plusDays(2);
		String eventKey = "membership-expired:" + membershipId + ":" + graceEndsOn;

		listener.onMembershipExpired(new MembershipExpiredV1(
				eventKey, organizationId, branchId, UUID.randomUUID(), membershipId, planId,
				endsOn, graceEndsOn, LocalDate.of(2026, 8, 3), OffsetDateTime.now()
		));

		verify(service).enqueueDomainEvent(
				eq(organizationId), eq(branchId), eq("MEMBERSHIP_EXPIRED"), eq(eventKey),
				argThat(payload -> payload.get("membershipId").equals(membershipId)
						&& payload.get("planId").equals(planId))
		);
	}

	@Test
	void exhaustedRecurringChargeFailureEntersTheDurableTrigger() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID chargeId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		String eventKey = "recurring-charge-failed:" + chargeId + ":3";

		listener.onRecurringChargeFailed(new RecurringChargeFailedV1(
				eventKey, organizationId, branchId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), chargeId, planId, UUID.randomUUID(), LocalDate.now(),
				3, true, "insufficient_funds", OffsetDateTime.now()
		));

		verify(service).enqueueDomainEvent(
				eq(organizationId), eq(branchId), eq("RECURRING_CHARGE_FAILED"), eq(eventKey),
				argThat(payload -> payload.get("chargeId").equals(chargeId)
						&& payload.get("planId").equals(planId)
						&& payload.get("exhausted").equals(true))
		);
	}
}
