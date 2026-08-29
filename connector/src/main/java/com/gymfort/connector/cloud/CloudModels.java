package com.gymfort.connector.cloud;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CloudModels {
    private CloudModels() {
    }

    public record ActivationResponse(
            UUID connectorId,
            String connectorKey,
            String providerKey,
            String connectorSecret,
            OffsetDateTime activatedAt,
            String warning
    ) {
    }

    public record HeartbeatResponse(
            String status,
            OffsetDateTime heartbeatAt,
            int pendingJobs,
            int failedJobs
    ) {
    }

    public record ClaimedJob(
            UUID id,
            String idempotencyKey,
            String action,
            UUID clientId,
            String externalPersonRef,
            List<String> desiredAccessLevelRefs,
            String status,
            int attempts,
            boolean retryable,
            OffsetDateTime leaseUntil,
            UUID claimToken,
            String lastError
    ) {
        public ClaimedJob {
            desiredAccessLevelRefs = desiredAccessLevelRefs == null ? List.of() : List.copyOf(desiredAccessLevelRefs);
        }
    }
}
