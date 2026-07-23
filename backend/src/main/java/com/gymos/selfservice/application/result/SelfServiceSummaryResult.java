package com.gymos.selfservice.application.result;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SelfServiceSummaryResult(
		UUID sessionId,
		OffsetDateTime expiresAt,
		String clientName,
		List<SelfServiceMembershipView> memberships,
		List<SelfServicePendingPaymentView> pendingPayments
) {
}
