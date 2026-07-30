package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipPeriodSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService implements MembershipOperations {
	private final MembershipQueryService queryService;
	private final MembershipRenewalService renewalService;
	private final MembershipPlanChangeService planChangeService;

	public MembershipService(
			MembershipQueryService queryService,
			MembershipRenewalService renewalService,
			MembershipPlanChangeService planChangeService
	) {
		this.queryService = queryService;
		this.renewalService = renewalService;
		this.planChangeService = planChangeService;
	}

	@Transactional
	public List<MembershipSummary> forClient(GymOsPrincipal principal, UUID clientId) {
		return queryService.forClient(principal, clientId);
	}

	@Transactional
	public MembershipSummary detail(GymOsPrincipal principal, UUID membershipId) {
		return queryService.detail(principal, membershipId);
	}

	public List<MembershipPeriodSummary> periods(GymOsPrincipal principal, UUID membershipId) {
		queryService.detail(principal, membershipId);
		return queryService.periods(principal, membershipId);
	}

	@Transactional
	public MembershipSaleResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipCommand command,
			String correlationId
	) {
		return renewalService.renew(principal, membershipId, command, correlationId);
	}

	@Override
	@Transactional
	public MembershipSaleOperationResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipOperationCommand command,
			String correlationId
	) {
		return MembershipResultMapper.toOperationResult(renew(principal, membershipId, new RenewMembershipCommand(
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				command.idempotencyKey()
		), correlationId));
	}

	@Transactional
	public MembershipSaleResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanCommand command,
			String correlationId
	) {
		return planChangeService.changePlan(principal, membershipId, command, correlationId);
	}

	@Override
	@Transactional
	public MembershipSaleOperationResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanOperationCommand command,
			String correlationId
	) {
		return MembershipResultMapper.toOperationResult(changePlan(principal, membershipId, new ChangeMembershipPlanCommand(
				command.targetPlanId(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				command.idempotencyKey()
		), correlationId));
	}
}
