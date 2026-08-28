package com.gymos.selfservice.application.result;

import com.gymos.selfservice.application.SelfServiceCapability;

public record SelfServiceCapabilities(
		boolean clientRegistrationAllowed,
		boolean membershipEnrollmentAllowed,
		boolean visitPackagePurchaseAllowed,
		boolean membershipRenewalAllowed,
		boolean membershipPlanChangeAllowed,
		boolean resourceBookingAllowed,
		boolean wellnessBookingAllowed,
		boolean profileUpdateAllowed,
		boolean autopayAllowed,
		boolean onlineCardPaymentsReady,
		boolean recurringCardPaymentsReady
) {
	public SelfServiceCapabilities withPaymentReadiness(boolean onlineCardPaymentsReady, boolean recurringCardPaymentsReady) {
		return new SelfServiceCapabilities(
				clientRegistrationAllowed,
				membershipEnrollmentAllowed,
				visitPackagePurchaseAllowed,
				membershipRenewalAllowed,
				membershipPlanChangeAllowed,
				resourceBookingAllowed,
				wellnessBookingAllowed,
				profileUpdateAllowed,
				autopayAllowed,
				onlineCardPaymentsReady,
				recurringCardPaymentsReady
		);
	}

	public SelfServiceCapabilities(
			boolean clientRegistrationAllowed,
			boolean membershipEnrollmentAllowed,
			boolean membershipRenewalAllowed,
			boolean membershipPlanChangeAllowed,
			boolean resourceBookingAllowed,
			boolean wellnessBookingAllowed,
			boolean profileUpdateAllowed,
			boolean autopayAllowed,
			boolean onlineCardPaymentsReady,
			boolean recurringCardPaymentsReady
	) {
		this(clientRegistrationAllowed, membershipEnrollmentAllowed, false, membershipRenewalAllowed,
				membershipPlanChangeAllowed, resourceBookingAllowed, wellnessBookingAllowed,
				profileUpdateAllowed, autopayAllowed, onlineCardPaymentsReady, recurringCardPaymentsReady);
	}

	public boolean allows(SelfServiceCapability capability) {
		return switch (capability) {
			case CLIENT_REGISTRATION -> clientRegistrationAllowed;
			case MEMBERSHIP_ENROLLMENT -> membershipEnrollmentAllowed;
			case VISIT_PACKAGE_PURCHASE -> visitPackagePurchaseAllowed;
			case MEMBERSHIP_RENEWAL -> membershipRenewalAllowed;
			case MEMBERSHIP_PLAN_CHANGE -> membershipPlanChangeAllowed;
			case RESOURCE_BOOKING -> resourceBookingAllowed;
			case WELLNESS_BOOKING -> wellnessBookingAllowed;
			case PROFILE_UPDATE -> profileUpdateAllowed;
			case AUTOPAY -> autopayAllowed;
		};
	}
}
