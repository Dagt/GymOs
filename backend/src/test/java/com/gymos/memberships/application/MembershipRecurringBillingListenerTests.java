package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.payments.api.RecurringChargeOperations;
import com.gymos.payments.api.RecurringChargeOperations.RecurringRenewalSale;
import com.gymos.payments.api.events.RecurringChargeDueV1;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MembershipRecurringBillingListenerTests {
	private final MembershipOperations membershipOperations = mock(MembershipOperations.class);
	private final RecurringChargeOperations recurringChargeOperations = mock(RecurringChargeOperations.class);
	private final MembershipRecurringBillingListener listener = new MembershipRecurringBillingListener(
			membershipOperations, recurringChargeOperations
	);

	@Test
	void dueChargeCreatesOneIdempotentRenewalSaleAndReturnsItToPayments() {
		RecurringChargeDueV1 event = dueEvent();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		when(membershipOperations.renew(
				any(), eq(event.membershipId()), any(), eq("recurring-charge:" + event.chargeId())
		)).thenReturn(new MembershipSaleOperationResult(
				saleId, "V-100", "PENDING_PAYMENT", 75_000, 0, 75_000, "MXN", null, paymentId
		));

		listener.onRecurringChargeDue(event);

		ArgumentCaptor<RenewMembershipOperationCommand> renewal = ArgumentCaptor.forClass(RenewMembershipOperationCommand.class);
		verify(membershipOperations).renew(
				any(), eq(event.membershipId()), renewal.capture(), eq("recurring-charge:" + event.chargeId())
		);
		assertThat(renewal.getValue().method()).isEqualTo("CARD_ONLINE");
		assertThat(renewal.getValue().idempotencyKey()).isEqualTo("recurring-charge:" + event.chargeId());
		verify(recurringChargeOperations).continueCharge(new RecurringRenewalSale(
				event.chargeId(), saleId, paymentId, 75_000, "MXN"
		));
	}

	@Test
	void successfulMembershipExtensionAdvancesTheNextChargeDate() {
		UUID organizationId = UUID.randomUUID();
		UUID sourceMembershipId = UUID.randomUUID();
		LocalDate renewedUntil = LocalDate.of(2026, 9, 30);

		listener.onMembershipRenewed(new MembershipRenewedV1(
				"membership-renewed:test", organizationId, UUID.randomUUID(), UUID.randomUUID(),
				sourceMembershipId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.of(2026, 9, 1), renewedUntil, OffsetDateTime.now()
		));

		verify(recurringChargeOperations).membershipRenewed(
				organizationId, sourceMembershipId, renewedUntil
		);
	}

	@Test
	void planChangeRevokesTheOldPriceConsent() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID sourceMembershipId = UUID.randomUUID();

		listener.onMembershipPlanChanged(new MembershipPlanChangedV1(
				"membership-plan-changed:test", organizationId, branchId, clientId,
				sourceMembershipId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.now(), LocalDate.now().plusMonths(1), OffsetDateTime.now()
		));

		verify(recurringChargeOperations).membershipPlanChanged(
				organizationId, branchId, clientId, sourceMembershipId, "membership-plan-changed:test"
		);
	}

	private RecurringChargeDueV1 dueEvent() {
		return new RecurringChargeDueV1(
				"recurring-charge-due:test", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.now(), 1, OffsetDateTime.now()
		);
	}
}
