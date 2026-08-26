package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipSaleIdempotencyHashTests {
	@Test
	void benefitSnapshotIsPartOfMembershipOperationIdentity() {
		UUID clientId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();

		String original = hash(
				clientId, membershipId, planId, productId,
				List.of(new MembershipBenefitSnapshot(benefitId, "STUDENT", "Estudiante", 1_000)));
		String changedDiscount = hash(
				clientId, membershipId, planId, productId,
				List.of(new MembershipBenefitSnapshot(benefitId, "STUDENT", "Estudiante", 2_000)));

		assertThat(changedDiscount).isNotEqualTo(original);
	}

	private String hash(
			UUID clientId,
			UUID membershipId,
			UUID planId,
			UUID productId,
			List<MembershipBenefitSnapshot> benefits
	) {
		return SalesIdempotency.membershipSaleRequestHash(
				clientId,
				membershipId,
				planId,
				productId,
				0,
				null,
				10_000,
				1_000,
				9_000,
				"MXN",
				benefits,
				"CASH",
				null,
				true,
				"Renovacion",
				"MEMBERSHIP_RENEWAL"
		);
	}
}
