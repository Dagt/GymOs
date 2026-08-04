package com.gymos.memberships.application;

import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.MembershipSummaryOperationResult;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.result.MembershipSummaryResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;

final class MembershipResultMapper {
	private MembershipResultMapper() {
	}

	static MembershipSaleResult toSaleResult(
			com.gymos.sales.api.operations.MembershipSaleResult sale,
			MembershipSummary membership
	) {
		return new MembershipSaleResult(
				sale.saleId(),
				sale.saleNumber(),
				sale.saleStatus(),
				sale.totalCents(),
				sale.paidCents(),
				sale.balanceCents(),
				sale.currency(),
				toSummaryResult(membership),
				sale.paymentId()
		);
	}

	static MembershipSaleOperationResult toOperationResult(MembershipSaleResult result) {
		return new MembershipSaleOperationResult(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				toOperationSummary(result.membership()),
				result.paymentId()
		);
	}

	private static MembershipSummaryResult toSummaryResult(MembershipSummary membership) {
		if (membership == null) {
			return null;
		}
		return new MembershipSummaryResult(
				membership.id(),
				membership.clientId(),
				membership.planId(),
				membership.planName(),
				membership.saleId(),
				membership.saleNumber(),
				membership.status(),
				membership.activatedAt(),
				membership.startsOn(),
				membership.endsOn(),
				membership.graceEndsOn()
		);
	}

	private static MembershipSummaryOperationResult toOperationSummary(MembershipSummaryResult membership) {
		if (membership == null) {
			return null;
		}
		return new MembershipSummaryOperationResult(
				membership.id(),
				membership.clientId(),
				membership.planId(),
				membership.planName(),
				membership.saleId(),
				membership.saleNumber(),
				membership.status(),
				membership.activatedAt(),
				membership.startsOn(),
				membership.endsOn(),
				membership.graceEndsOn()
		);
	}
}
