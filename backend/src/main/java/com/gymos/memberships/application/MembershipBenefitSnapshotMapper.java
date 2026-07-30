package com.gymos.memberships.application;

import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import java.util.List;

final class MembershipBenefitSnapshotMapper {
	private MembershipBenefitSnapshotMapper() {
	}

	static List<MembershipBenefitSnapshot> from(MembershipQuoteResult quote) {
		return quote.appliedBenefits().stream()
				.map(benefit -> new MembershipBenefitSnapshot(
						benefit.benefitId(),
						benefit.code(),
						benefit.name(),
						benefit.discountCents()
				))
				.toList();
	}
}
