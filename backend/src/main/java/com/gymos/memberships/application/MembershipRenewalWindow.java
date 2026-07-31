package com.gymos.memberships.application;

import java.time.LocalDate;

record MembershipRenewalWindow(
		LocalDate membershipStartsOn,
		LocalDate periodStartsOn,
		LocalDate endsOn,
		LocalDate graceEndsOn
) {
}
