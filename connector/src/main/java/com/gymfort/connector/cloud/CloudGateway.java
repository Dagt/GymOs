package com.gymfort.connector.cloud;

import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import java.util.List;
import java.util.UUID;

public interface CloudGateway extends AutoCloseable {
    List<ClaimedJob> claim(int limit, int leaseSeconds);

    void complete(UUID jobId, UUID claimToken, String externalOperationRef);

    void fail(UUID jobId, UUID claimToken, String error, boolean retryable, int retryAfterSeconds);

    void extendLease(UUID jobId, UUID claimToken, int leaseSeconds);

    void heartbeat(String status, String connectorVersion, String instanceId, String lastError);

    @Override
    default void close() {
    }
}
