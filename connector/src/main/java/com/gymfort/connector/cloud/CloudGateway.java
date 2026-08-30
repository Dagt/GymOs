package com.gymfort.connector.cloud;

import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.gymfort.connector.cloud.CloudModels.PersonCandidate;
import com.gymfort.connector.cloud.CloudModels.DeviceCandidate;
import com.gymfort.connector.cloud.CloudModels.AuthorizationRequest;
import com.gymfort.connector.cloud.CloudModels.AuthorizationResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CloudGateway extends AutoCloseable {
    List<ClaimedJob> claim(int limit, int leaseSeconds);

    void complete(UUID jobId, UUID claimToken, String externalOperationRef);

    void fail(UUID jobId, UUID claimToken, String error, boolean retryable, int retryAfterSeconds);

    void extendLease(UUID jobId, UUID claimToken, int leaseSeconds);

    void heartbeat(String status, String connectorVersion, String instanceId, String lastError);

    default void heartbeat(
            String status, String connectorVersion, String instanceId,
            String lastError, Set<String> capabilities
    ) {
        heartbeat(status, connectorVersion, instanceId, lastError);
    }

    default void publishPerson(PersonCandidate candidate) {
    }

    default void publishDevice(DeviceCandidate candidate) {
    }

    default AuthorizationResponse authorize(AuthorizationRequest request) {
        throw new UnsupportedOperationException("Cloud authorization is not implemented.");
    }

    default void reportActuation(
            UUID authorizationId, String status, String externalOperationRef, String error
    ) {
    }

    @Override
    default void close() {
    }
}
