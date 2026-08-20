package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import org.junit.jupiter.api.Test;

class SelfServiceCapabilityPolicyTests {
	private final SelfServiceCapabilityPolicy policy =
			new SelfServiceCapabilityPolicy(new ObjectMapper());

	@Test
	void resolvesCanonicalCapabilitiesButKeepsPaymentReadinessDisabled() {
		var capabilities = policy.resolve("""
				{
				  "membershipRenewal": true,
				  "membershipPlanChange": true,
				  "resourceBooking": false,
				  "wellnessBooking": true,
				  "profileUpdate": true,
				  "autopay": true
				}
				""");

		assertThat(capabilities.membershipRenewalAllowed()).isTrue();
		assertThat(capabilities.membershipPlanChangeAllowed()).isTrue();
		assertThat(capabilities.resourceBookingAllowed()).isFalse();
		assertThat(capabilities.wellnessBookingAllowed()).isTrue();
		assertThat(capabilities.profileUpdateAllowed()).isTrue();
		assertThat(capabilities.autopayAllowed()).isTrue();
		assertThat(capabilities.onlineCardPaymentsReady()).isFalse();
		assertThat(capabilities.recurringCardPaymentsReady()).isFalse();
	}

	@Test
	void acceptsLegacyKeysDuringMigration() {
		var capabilities = policy.resolve("""
				{"renew":true,"changePlan":true,"booking":true,"contactUpdate":true}
				""");

		assertThat(capabilities.membershipRenewalAllowed()).isTrue();
		assertThat(capabilities.membershipPlanChangeAllowed()).isTrue();
		assertThat(capabilities.resourceBookingAllowed()).isTrue();
		assertThat(capabilities.profileUpdateAllowed()).isTrue();
	}

	@Test
	void rejectsDisabledCapability() {
		var capabilities = policy.resolve("{}");

		assertThatThrownBy(() -> policy.require(
				capabilities,
				SelfServiceCapability.PROFILE_UPDATE
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("SELF_SERVICE_ACTION_NOT_ALLOWED")
				);
	}
}
