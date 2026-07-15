package com.gymos.memberships.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.util.UUID;

public interface MembershipOperations {
	MembershipSaleOperationResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipOperationCommand command,
			String correlationId
	);

	MembershipSaleOperationResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanOperationCommand command,
			String correlationId
	);
}
