package com.gymos.selfservice.application.result;

import com.gymos.selfservice.application.SelfServiceCapability;

public record SelfServiceCapabilities(
		boolean membershipRenewalAllowed,
		boolean membershipPlanChangeAllowed,
		boolean resourceBookingAllowed,
		boolean wellnessBookingAllowed,
		boolean profileUpdateAllowed,
		boolean autopayAllowed,
		boolean onlineCardPaymentsReady,
		boolean recurringCardPaymentsReady
) {
	public boolean allows(SelfServiceCapability capability) {
		return switch (capability) {
			case MEMBERSHIP_RENEWAL -> membershipRenewalAllowed;
			case MEMBERSHIP_PLAN_CHANGE -> membershipPlanChangeAllowed;
			case RESOURCE_BOOKING -> resourceBookingAllowed;
			case WELLNESS_BOOKING -> wellnessBookingAllowed;
			case PROFILE_UPDATE -> profileUpdateAllowed;
			case AUTOPAY -> autopayAllowed;
		};
	}
}
