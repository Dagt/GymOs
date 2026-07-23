package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.MembershipSummaryOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.result.MembershipSummaryResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService implements MembershipOperations {
	private final MembershipRepository membershipRepository;
	private final SalesOperations salesOperations;
	private final MembershipActivationService membershipActivationService;

	public MembershipService(
			MembershipRepository membershipRepository,
			SalesOperations salesOperations,
			MembershipActivationService membershipActivationService
	) {
		this.membershipRepository = membershipRepository;
		this.salesOperations = salesOperations;
		this.membershipActivationService = membershipActivationService;
	}

	@Transactional(readOnly = true)
	public List<MembershipSummary> forClient(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		if (!membershipRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		return membershipRepository.forClient(principal.organizationId(), principal.branchId(), clientId);
	}

	@Transactional(readOnly = true)
	public MembershipSummary detail(GymOsPrincipal principal, UUID membershipId) {
		requireBranch(principal);
		return membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
	}

	@Transactional
	public MembershipSaleResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipCommand command,
			String correlationId
	) {
		requireBranch(principal);
		MembershipSummary current = membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_RENEWABLE", "Solo puedes renovar una membresia activa.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), current.planId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan de la membresia no esta disponible para renovacion."));

		var sale = salesOperations.createMembershipRenewal(new CreateMembershipRenewalSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				current.clientId(),
				current.id(),
				plan.planId(),
				plan.productId(),
				plan.planName(),
				plan.priceCents(),
				plan.currency(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				defaultIdempotencyKey("membership-renewal", membershipId, command.idempotencyKey()),
				correlationId
		));
		MembershipSummary renewed = sale.paymentCompleted()
				? membershipRepository.bySaleAndPlan(principal.organizationId(), principal.branchId(), sale.saleId(), plan.planId()).orElse(null)
				: null;
		return toMembershipSaleResult(sale, renewed);
	}

	@Override
	@Transactional
	public MembershipSaleOperationResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipOperationCommand command,
			String correlationId
	) {
		return toOperationResult(renew(principal, membershipId, new RenewMembershipCommand(
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
		requireBranch(principal);
		MembershipSummary current = membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_CHANGEABLE", "Solo puedes cambiar una membresia activa.");
		}
		if (current.planId().equals(command.targetPlanId())) {
			throw new BusinessException("MEMBERSHIP_PLAN_UNCHANGED", "Selecciona un plan diferente.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), command.targetPlanId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan seleccionado no esta disponible."));
		String method = command.method().trim().toUpperCase();
		if (!command.validateNow() && !"CASH".equals(method)) {
			throw new BusinessException("PLAN_CHANGE_REQUIRES_COMPLETED_PAYMENT", "El cambio de plan requiere pago completado.");
		}

		var sale = salesOperations.createMembershipPlanChange(new CreateMembershipPlanChangeSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				current.clientId(),
				current.id(),
				plan.planId(),
				plan.productId(),
				plan.planName(),
				plan.priceCents(),
				plan.currency(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				defaultIdempotencyKey("membership-plan-change", membershipId, command.idempotencyKey()),
				correlationId
		));
		MembershipSummary changed = sale.paymentCompleted()
				? membershipRepository.bySaleAndPlan(principal.organizationId(), principal.branchId(), sale.saleId(), plan.planId()).orElse(null)
				: null;
		return toMembershipSaleResult(sale, changed);
	}

	@Override
	@Transactional
	public MembershipSaleOperationResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanOperationCommand command,
			String correlationId
	) {
		return toOperationResult(changePlan(principal, membershipId, new ChangeMembershipPlanCommand(
				command.targetPlanId(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				command.idempotencyKey()
		), correlationId));
	}

	@EventListener
	@Transactional
	public void onPaymentCompleted(PaymentCompletedV1 event) {
		membershipActivationService.processPaymentCompleted(event);
	}

	private MembershipSaleResult toMembershipSaleResult(com.gymos.sales.api.operations.MembershipSaleResult sale, MembershipSummary membership) {
		return new MembershipSaleResult(
				sale.saleId(),
				sale.saleNumber(),
				sale.saleStatus(),
				sale.totalCents(),
				sale.paidCents(),
				sale.balanceCents(),
				sale.currency(),
				toMembershipSummaryResult(membership)
		);
	}

	private MembershipSummaryResult toMembershipSummaryResult(MembershipSummary membership) {
		if (membership == null) {
			return null;
		}
		return new MembershipSummaryResult(
				membership.id(),
				membership.clientId(),
				membership.planId(),
				membership.planName(),
				membership.saleId(),
				membership.saleNumber(),
				membership.status(),
				membership.activatedAt(),
				membership.startsOn(),
				membership.endsOn(),
				membership.graceEndsOn()
		);
	}

	private MembershipSaleOperationResult toOperationResult(MembershipSaleResult result) {
		return new MembershipSaleOperationResult(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				toOperationSummary(result.membership())
		);
	}

	private MembershipSummaryOperationResult toOperationSummary(MembershipSummaryResult membership) {
		if (membership == null) {
			return null;
		}
		return new MembershipSummaryOperationResult(
				membership.id(),
				membership.clientId(),
				membership.planId(),
				membership.planName(),
				membership.saleId(),
				membership.saleNumber(),
				membership.status(),
				membership.activatedAt(),
				membership.startsOn(),
				membership.endsOn(),
				membership.graceEndsOn()
		);
	}

	private String defaultIdempotencyKey(String operation, UUID membershipId, String provided) {
		return provided == null || provided.isBlank() ? operation + ":" + membershipId + ":" + UUID.randomUUID() : provided.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
