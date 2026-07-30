package com.gymos.memberships.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class MembershipActivationService {
	private final MembershipRepository membershipRepository;
	private final AuditService auditService;
	private final MembershipActivationWindowCalculator windowCalculator;
	private final MembershipRenewalWindowCalculator renewalWindowCalculator;
	private final MembershipActivationEventPublisher eventPublisher;

	MembershipActivationService(
			MembershipRepository membershipRepository,
			AuditService auditService,
			MembershipActivationWindowCalculator windowCalculator,
			MembershipRenewalWindowCalculator renewalWindowCalculator,
			MembershipActivationEventPublisher eventPublisher
	) {
		this.membershipRepository = membershipRepository;
		this.auditService = auditService;
		this.windowCalculator = windowCalculator;
		this.renewalWindowCalculator = renewalWindowCalculator;
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
		MembershipSummary current = membershipRepository.byIdForUpdate(event.organizationId(), event.branchId(), event.contextMembershipId())
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status()) && !"EXPIRED".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_RENEWABLE", "La membresia ya no se puede renovar.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(event.organizationId(), current.planId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan de la membresia no esta disponible para renovacion."));

		java.time.LocalDate businessDate = MembershipBusinessDate.from(event.occurredAt());
		MembershipRenewalWindow window = renewalWindowCalculator.calculate(current, plan, businessDate);

		if (membershipRepository.hasOtherActiveMembership(event.organizationId(), current.clientId(), current.id())) {
			throw new BusinessException("MEMBERSHIP_ACTIVE_CONFLICT", "El socio ya tiene otra membresia activa.");
		}
		int updated = membershipRepository.updateMembershipForRenewal(
				event.organizationId(),
				current.id(),
				event.saleId(),
				window.membershipStartsOn(),
				window.endsOn(),
				window.graceEndsOn(),
				event.eventKey()
		);
		if (updated != 1) {
			throw new BusinessException("MEMBERSHIP_RENEWAL_CONFLICT", "La membresia cambio durante la renovacion.");
		}
		membershipRepository.insertPeriod(
				event.organizationId(), event.branchId(), current.id(), current.clientId(), current.planId(),
				event.saleId(), "RENEWAL", window.periodStartsOn(), window.endsOn(), event.eventKey());
		auditService.recordSystem(event.organizationId(), event.branchId(), null, "MEMBERSHIP_RENEWED", "MEMBERSHIP", current.id(), event.businessContext(), null, event.eventKey());
		MembershipSummary renewed = membershipRepository.byId(event.organizationId(), event.branchId(), current.id())
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia renovada no esta disponible."));
		eventPublisher.publish(event, current.id(), renewed, "MEMBERSHIP_RENEWED");
	}

	private void processPlanChangePayment(PaymentCompletedV1 event) {
		if (event.contextMembershipId() == null || event.contextTargetPlanId() == null) {
			throw new BusinessException("MEMBERSHIP_CONTEXT_REQUIRED", "El cambio de plan necesita membresia origen y plan destino.");
		}
		MembershipSummary current = membershipRepository.byIdForUpdate(event.organizationId(), event.branchId(), event.contextMembershipId())
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status()) && !"EXPIRED".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_CHANGEABLE", "La membresia ya no se puede cambiar de plan.");
		}
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
		if (membershipRepository.hasOtherActiveMembership(event.organizationId(), event.clientId(), sourceMembershipId)) {
			throw new BusinessException("MEMBERSHIP_ACTIVE_CONFLICT", "El socio ya tiene una membresia activa.");
		}
		MembershipActivationWindow window = windowCalculator.calculate(event, plan);
		UUID membershipId = membershipRepository.insertActiveMembership(
				event.organizationId(),
				event.branchId(),
				event.clientId(),
				plan.planId(),
				event.saleId(),
				window.startsOn(),
				window.endsOn(),
				window.graceEndsOn(),
				sourceMembershipId,
				event.eventKey()
		);
		membershipRepository.insertPeriod(
				event.organizationId(), event.branchId(), membershipId, event.clientId(), plan.planId(),
				event.saleId(), "MEMBERSHIP_PLAN_CHANGED".equals(action) ? "PLAN_CHANGE" : "ENROLLMENT",
				window.startsOn(), window.endsOn(), event.eventKey());
		auditService.recordSystem(event.organizationId(), event.branchId(), null, action, "MEMBERSHIP", membershipId, event.businessContext(), null, event.eventKey());
		MembershipSummary membership = membershipRepository.byId(event.organizationId(), event.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia activada no esta disponible."));
		eventPublisher.publish(event, sourceMembershipId, membership, action);
		return membership;
	}

	private void cancelForPlanChange(PaymentCompletedV1 event, MembershipSummary current) {
		int updated = membershipRepository.cancelActiveForPlanChange(event.organizationId(), event.branchId(), current.id());
		if (updated != 1) {
			throw new BusinessException("MEMBERSHIP_PLAN_CHANGE_CONFLICT", "La membresia cambio antes de completar el cambio de plan.");
		}
		auditService.recordSystem(event.organizationId(), event.branchId(), null, "MEMBERSHIP_CANCELLED_BY_PLAN_CHANGE", "MEMBERSHIP", current.id(), event.businessContext(), null, event.eventKey());
	}
}
