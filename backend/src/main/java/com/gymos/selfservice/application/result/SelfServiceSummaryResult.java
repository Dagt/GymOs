package com.gymos.selfservice.application.result;

import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SelfServicePaymentRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SelfServiceSummaryResult(
		UUID sessionId,
		OffsetDateTime expiresAt,
		String clientName,
		SelfServiceClientProfile profile,
		SelfServiceCapabilities capabilities,
		List<MembershipRecord> memberships,
		List<PendingPaymentRecord> pendingPayments,
		List<SelfServicePaymentRecord> paymentActivity
) {
}
