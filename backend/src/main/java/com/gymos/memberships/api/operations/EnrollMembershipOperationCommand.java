package com.gymos.memberships.api.operations;

import java.util.UUID;

public record EnrollMembershipOperationCommand(
        UUID clientId,
        UUID planId,
        String method,
        String reference,
        boolean validateNow,
        String notes,
        String idempotencyKey
) {
}
