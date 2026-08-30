package com.gymfort.connector.cloud;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;

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

    public record PersonCandidate(
            String externalPersonRef,
            String externalDisplayName,
            String email,
            String phone,
            String externalEmployeeNumber
    ) {
    }

    public record DeviceCandidate(
            String externalDeviceRef,
            String externalDeviceName,
            String externalDeviceType,
            Map<String, String> metadata
    ) {
        public DeviceCandidate {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record AuthorizationRequest(
            String providerKey,
            String externalEventId,
            String externalPersonRef,
            String externalDeviceRef,
            OffsetDateTime occurredAt,
            String providerDecision,
            String eventType,
            Map<String, String> metadata
    ) {
        public AuthorizationRequest {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record AuthorizationResponse(
            UUID authorizationId,
            UUID accessLogId,
            String externalEventId,
            String decision,
            String authorizationMode,
            String actuationStatus,
            boolean shouldOpen,
            String reason,
            OffsetDateTime updatedAt
    ) {
    }
}
