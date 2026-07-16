package com.gymos.selfservice.application.result;

import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SelfServiceSummaryResult(
		UUID sessionId,
		OffsetDateTime expiresAt,
		String clientName,
		List<MembershipRecord> memberships,
		List<PendingPaymentRecord> pendingPayments
) {
}
