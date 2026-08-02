package com.gymos.memberships.api.operations;

import com.gymos.common.security.GymOsPrincipal;

public interface MembershipEnrollmentOperations {
    MembershipSaleOperationResult enroll(
            GymOsPrincipal principal,
            EnrollMembershipOperationCommand command,
            String correlationId
    );
}
