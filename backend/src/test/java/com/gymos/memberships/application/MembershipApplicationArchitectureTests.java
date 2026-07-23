package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MembershipApplicationArchitectureTests {
	@Test
	void membershipRenewalDelegatesSharedOperationResponsibilities() {
		assertThat(Arrays.stream(MembershipRenewalService.class.getDeclaredFields()).map(field -> field.getType().getSimpleName()).toList())
				.containsExactlyInAnyOrder("MembershipOperationPolicy", "MembershipOperationIdempotency", "SalesOperations", "MembershipSaleResultResolver")
				.doesNotContain("MembershipRepository");
	}

	@Test
	void membershipPlanChangeDelegatesSharedOperationResponsibilities() {
		assertThat(Arrays.stream(MembershipPlanChangeService.class.getDeclaredFields()).map(field -> field.getType().getSimpleName()).toList())
				.containsExactlyInAnyOrder("MembershipOperationPolicy", "MembershipOperationIdempotency", "SalesOperations", "MembershipSaleResultResolver")
				.doesNotContain("MembershipRepository");
	}

	@Test
	void membershipServiceRemainsAStableFacade() {
		assertThat(Arrays.stream(MembershipService.class.getDeclaredFields()).map(field -> field.getType().getSimpleName()).toList())
				.doesNotContain("MembershipRepository");
	}
}
