package com.gymos.memberships.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {
	private final MembershipClientRepository clients;
	private final MembershipQueryRepository queries;
	private final MembershipPlanLookupRepository plans;
	private final MembershipEventRepository events;
	private final MembershipCommandRepository commands;

	public MembershipRepository(
			MembershipClientRepository clients,
			MembershipQueryRepository queries,
			MembershipPlanLookupRepository plans,
			MembershipEventRepository events,
			MembershipCommandRepository commands
	) {
		this.clients = clients;
		this.queries = queries;
		this.plans = plans;
		this.events = events;
		this.commands = commands;
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		return clients.exists(organizationId, clientId);
	}

	public List<MembershipSummary> forClient(UUID organizationId, UUID branchId, UUID clientId) {
		return queries.forClient(organizationId, branchId, clientId);
	}

	public Optional<MembershipSummary> byId(UUID organizationId, UUID branchId, UUID membershipId) {
		return queries.byId(organizationId, branchId, membershipId);
	}

	public Optional<MembershipSummary> bySaleAndPlan(UUID organizationId, UUID branchId, UUID saleId, UUID planId) {
		return queries.bySaleAndPlan(organizationId, branchId, saleId, planId);
	}

	public Optional<PlanForRenewal> planForRenewal(UUID organizationId, UUID planId) {
		return plans.forRenewal(organizationId, planId);
	}

	public Optional<PlanForRenewal> planForProduct(UUID organizationId, UUID productId) {
		return plans.forProduct(organizationId, productId);
	}

	public boolean markEventProcessed(UUID organizationId, String eventKey, String eventType) {
		return events.markProcessed(organizationId, eventKey, eventType);
	}

	public Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
		return commands.latestMembershipEnd(organizationId, clientId, planId);
	}

	public UUID insertActiveMembership(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID planId,
			UUID saleId,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn,
			UUID sourceMembershipId,
			String sourcePaymentEventKey
	) {
		return commands.insertActiveMembership(
				organizationId,
				branchId,
				clientId,
				planId,
				saleId,
				startsOn,
				endsOn,
				graceEndsOn,
				sourceMembershipId,
				sourcePaymentEventKey
		);
	}

	public int updateMembershipForRenewal(
			UUID organizationId,
			UUID membershipId,
			UUID saleId,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn,
			String eventKey
	) {
		return commands.updateMembershipForRenewal(
				organizationId,
				membershipId,
				saleId,
				startsOn,
				endsOn,
				graceEndsOn,
				eventKey
		);
	}

	public int cancelActiveForPlanChange(UUID organizationId, UUID branchId, UUID membershipId) {
		return commands.cancelActiveForPlanChange(organizationId, branchId, membershipId);
	}

	public record MembershipSummary(
			UUID id,
			UUID clientId,
			UUID planId,
			String planName,
			UUID saleId,
			String saleNumber,
			String status,
			OffsetDateTime activatedAt,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
	}

	public record PlanForRenewal(
			UUID planId,
			UUID productId,
			String planName,
			int durationDays,
			int graceDays,
			boolean multiBranchAccess,
			long priceCents,
			String currency
	) {
	}
}
