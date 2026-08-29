package com.gymfort.connector.provider;

import java.util.List;
import java.util.UUID;

public record ProviderSyncCommand(
        UUID jobId,
        String idempotencyKey,
        Action action,
        UUID clientId,
        String externalPersonRef,
        List<String> desiredAccessLevelRefs
) {
    public ProviderSyncCommand {
        desiredAccessLevelRefs = desiredAccessLevelRefs == null
                ? List.of()
                : desiredAccessLevelRefs.stream().distinct().sorted().toList();
    }

    public enum Action {
        UPSERT,
        REVOKE,
        RECONCILE
    }
}
