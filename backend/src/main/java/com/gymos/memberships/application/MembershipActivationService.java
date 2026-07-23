package com.gymos.memberships.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.memberships.api.events.MembershipActivatedV1;
import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class MembershipActivationService {
	private final MembershipRepository membershipRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	MembershipActivationService(
			MembershipRepository membershipRepository,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.membershipRepository = membershipRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	void processPaymentCompleted(PaymentCompletedV1 event) {
		if (!membershipRepository.markEventProcessed(event.organizationId(), event.eventKey(), event.getClass().getSimpleName())) {
			return;
		}
		if (event.clientId() == null) {
			return;
		}
		if ("MEMBERSHIP_RENEWAL".equals(event.businessContext())) {
			processRenewalPayment(event);
			return;
		}
		if ("MEMBERSHIP_PLAN_CHANGE".equals(event.businessContext())) {
			processPlanChangePayment(event);
			return;
		}
		for (PaymentCompletedLineItemV1 item : event.items()) {
			membershipRepository.planForProduct(event.organizationId(), item.productId())
					.ifPresent(plan -> activateMembershipForSale(event, null, plan, "MEMBERSHIP_ACTIVATED"));
		}
	}

	private void processRenewalPayment(PaymentCompletedV1 event) {
		if (event.contextMembershipId() == null) {
			throw new BusinessException("MEMBERSHIP_CONTEXT_REQUIRED", "La renovacion necesita membresia origen.");
		}
		MembershipSummary current = membershipRepository.byId(event.organizationId(), event.branchId(), event.contextMembershipId())
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		PlanForRenewal plan = membershipRepository.planForRenewal(event.organizationId(), current.planId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan de la membresia no esta disponible para renovacion."));
		activateMembershipForSale(event, current.id(), plan, "MEMBERSHIP_RENEWED");
	}

	private void processPlanChangePayment(PaymentCompletedV1 event) {
		if (event.contextMembershipId() == null || event.contextTargetPlanId() == null) {
			throw new BusinessException("MEMBERSHIP_CONTEXT_REQUIRED", "El cambio de plan necesita membresia origen y plan destino.");
		}
		MembershipSummary current = membershipRepository.byId(event.organizationId(), event.branchId(), event.contextMembershipId())
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		PlanForRenewal targetPlan = membershipRepository.planForRenewal(event.organizationId(), event.contextTargetPlanId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan seleccionado no esta disponible."));
		cancelForPlanChange(event, current);
		activateMembershipForSale(event, current.id(), targetPlan, "MEMBERSHIP_PLAN_CHANGED");
	}

	private MembershipSummary activateMembershipForSale(
			PaymentCompletedV1 event,
			UUID sourceMembershipId,
			PlanForRenewal plan,
			String action
	) {
		return membershipRepository.bySaleAndPlan(event.organizationId(), event.branchId(), event.saleId(), plan.planId())
				.orElseGet(() -> insertMembership(event, sourceMembershipId, plan, action));
	}

	private MembershipSummary insertMembership(
			PaymentCompletedV1 event,
			UUID sourceMembershipId,
			PlanForRenewal plan,
			String action
	) {
		LocalDate businessDate = MembershipBusinessDate.from(event.occurredAt());
		LocalDate latestEnd = membershipRepository.latestMembershipEnd(event.organizationId(), event.clientId(), plan.planId())
				.orElse(null);
		LocalDate startsOn = latestEnd != null && !latestEnd.isBefore(businessDate) ? latestEnd.plusDays(1) : businessDate;
		if ("MEMBERSHIP_PLAN_CHANGED".equals(action)) {
			startsOn = businessDate;
		}
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		LocalDate graceEndsOn = endsOn.plusDays(plan.graceDays());
		UUID membershipId = membershipRepository.insertActiveMembership(
				event.organizationId(),
				event.branchId(),
				event.clientId(),
				plan.planId(),
				event.saleId(),
				startsOn,
				endsOn,
				graceEndsOn,
				sourceMembershipId,
				event.eventKey()
		);
		auditService.recordSystem(event.organizationId(), event.branchId(), null, action, "MEMBERSHIP", membershipId, event.businessContext(), null, event.eventKey());
		MembershipSummary membership = membershipRepository.byId(event.organizationId(), event.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia activada no esta disponible."));
		publishMembershipEvent(event, sourceMembershipId, membership, action);
		return membership;
	}

	private void cancelForPlanChange(PaymentCompletedV1 event, MembershipSummary current) {
		membershipRepository.cancelActiveForPlanChange(event.organizationId(), event.branchId(), current.id());
		auditService.recordSystem(event.organizationId(), event.branchId(), null, "MEMBERSHIP_CANCELLED_BY_PLAN_CHANGE", "MEMBERSHIP", current.id(), event.businessContext(), null, event.eventKey());
	}

	private void publishMembershipEvent(PaymentCompletedV1 paymentEvent, UUID sourceMembershipId, MembershipSummary membership, String action) {
		if ("MEMBERSHIP_RENEWED".equals(action)) {
			eventPublisher.publishEvent(new MembershipRenewedV1(
					paymentEvent.eventKey() + ":membership-renewed",
					paymentEvent.organizationId(),
					paymentEvent.branchId(),
					paymentEvent.clientId(),
					sourceMembershipId,
					membership.id(),
					membership.planId(),
					paymentEvent.saleId(),
					membership.startsOn(),
					membership.endsOn(),
					OffsetDateTime.now()
			));
			return;
		}
		if ("MEMBERSHIP_PLAN_CHANGED".equals(action)) {
			eventPublisher.publishEvent(new MembershipPlanChangedV1(
					paymentEvent.eventKey() + ":membership-plan-changed",
					paymentEvent.organizationId(),
					paymentEvent.branchId(),
					paymentEvent.clientId(),
					sourceMembershipId,
					membership.id(),
					membership.planId(),
					paymentEvent.saleId(),
					membership.startsOn(),
					membership.endsOn(),
					OffsetDateTime.now()
			));
			return;
		}
		eventPublisher.publishEvent(new MembershipActivatedV1(
				paymentEvent.eventKey() + ":membership-activated:" + membership.planId(),
				paymentEvent.organizationId(),
				paymentEvent.branchId(),
				paymentEvent.clientId(),
				membership.id(),
				membership.planId(),
				paymentEvent.saleId(),
				membership.startsOn(),
				membership.endsOn(),
				OffsetDateTime.now()
		));
	}
}
