package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.payments.api.RecurringChargeOperations;
import com.gymos.payments.api.RecurringChargeOperations.RecurringRenewalSale;
import com.gymos.payments.api.events.RecurringChargeDueV1;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class MembershipRecurringBillingListener {
	private final MembershipOperations membershipOperations;
	private final RecurringChargeOperations recurringChargeOperations;

	public MembershipRecurringBillingListener(
			MembershipOperations membershipOperations,
			RecurringChargeOperations recurringChargeOperations
	) {
		this.membershipOperations = membershipOperations;
		this.recurringChargeOperations = recurringChargeOperations;
	}

	@ApplicationModuleListener
	public void onRecurringChargeDue(RecurringChargeDueV1 event) {
		String key = "recurring-charge:" + event.chargeId();
		try {
			MembershipSaleOperationResult sale = membershipOperations.renew(
					new GymOsPrincipal(
							null, event.organizationId(), event.branchId(),
							"recurring-billing", "Cobro recurrente", Set.of()
					),
					event.membershipId(),
					new RenewMembershipOperationCommand(
							"CARD_ONLINE", null, false, "Renovacion automatica con Stripe", key
					),
					key
			);
			recurringChargeOperations.continueCharge(new RecurringRenewalSale(
					event.chargeId(), sale.saleId(), sale.paymentId(), sale.balanceCents(), sale.currency()
			));
		} catch (RuntimeException exception) {
			recurringChargeOperations.preparationFailed(event.chargeId(), safeCode(exception));
		}
	}

	@ApplicationModuleListener
	public void onMembershipRenewed(MembershipRenewedV1 event) {
		recurringChargeOperations.membershipRenewed(
				event.organizationId(), event.sourceMembershipId(), event.endsOn()
		);
	}

	@ApplicationModuleListener
	public void onMembershipPlanChanged(MembershipPlanChangedV1 event) {
		recurringChargeOperations.membershipPlanChanged(
				event.organizationId(), event.branchId(), event.clientId(), event.sourceMembershipId(), event.eventKey()
		);
	}

	private String safeCode(RuntimeException exception) {
		return exception instanceof BusinessException businessException
				? businessException.code()
				: exception.getClass().getSimpleName();
	}
}
