package com.gymos.memberships.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.api.operations.AutomationDomainEvents.DomainEvent;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipPeriodSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService implements MembershipOperations {
	private final MembershipQueryService queryService;
	private final MembershipRenewalService renewalService;
	private final MembershipPlanChangeService planChangeService;
	private final AutomationDomainEvents domainEvents;
	private final ObjectMapper objectMapper;

	public MembershipService(
			MembershipQueryService queryService,
			MembershipRenewalService renewalService,
			MembershipPlanChangeService planChangeService,
			AutomationDomainEvents domainEvents,
			ObjectMapper objectMapper
	) {
		this.queryService = queryService;
		this.renewalService = renewalService;
		this.planChangeService = planChangeService;
		this.domainEvents = domainEvents;
		this.objectMapper = objectMapper;
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
		MembershipSaleResult result = renewalService.renew(principal, membershipId, command, correlationId);
		if (result.membership() != null) {
			publish(principal, "MEMBERSHIP_RENEWED", "membership-renewed:" + membershipId + ":" + result.saleId(),
					Map.of(
							"membershipId", membershipId.toString(),
							"saleId", result.saleId().toString(),
							"saleStatus", result.saleStatus() == null ? "UNKNOWN" : result.saleStatus(),
							"endsOn", result.membership().endsOn().toString()
					));
		}
		return result;
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
				command.method(), command.reference(), command.validateNow(), command.notes(), command.idempotencyKey()
		), correlationId));
	}

	@Transactional
	public MembershipSaleResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanCommand command,
			String correlationId
	) {
		MembershipSaleResult result = planChangeService.changePlan(principal, membershipId, command, correlationId);
		if (result.membership() != null) {
			publish(principal, "MEMBERSHIP_PLAN_CHANGED", "membership-plan-changed:" + membershipId + ":" + result.saleId(),
					Map.of(
							"membershipId", membershipId.toString(),
							"targetPlanId", command.targetPlanId().toString(),
							"saleId", result.saleId().toString(),
							"saleStatus", result.saleStatus() == null ? "UNKNOWN" : result.saleStatus()
					));
		}
		return result;
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
				command.targetPlanId(), command.method(), command.reference(), command.validateNow(),
				command.notes(), command.idempotencyKey()
		), correlationId));
	}

	private void publish(GymOsPrincipal principal, String eventType, String eventId, Map<String, String> payload) {
		try {
			domainEvents.publish(new DomainEvent(
					principal.organizationId(), principal.branchId(), eventType, eventId, 1,
					OffsetDateTime.now(), objectMapper.writeValueAsString(payload)
			));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize membership domain event", exception);
		}
	}
}
