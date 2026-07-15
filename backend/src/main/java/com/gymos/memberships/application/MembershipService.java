package com.gymos.memberships.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.events.MembershipActivatedV1;
import com.gymos.common.events.MembershipPlanChangedV1;
import com.gymos.common.events.MembershipRenewedV1;
import com.gymos.common.events.PaymentCompletedLineItemV1;
import com.gymos.common.events.PaymentCompletedV1;
import com.gymos.common.security.GymOsPrincipal;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {
	private final JdbcClient jdbcClient;
	private final MembershipRepository membershipRepository;
	private final SalesOperations salesOperations;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	public MembershipService(
			JdbcClient jdbcClient,
			MembershipRepository membershipRepository,
			SalesOperations salesOperations,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.jdbcClient = jdbcClient;
		this.membershipRepository = membershipRepository;
		this.salesOperations = salesOperations;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
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

	@EventListener
	@Transactional
	public void onPaymentCompleted(PaymentCompletedV1 event) {
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
		OffsetDateTime now = OffsetDateTime.now();
		LocalDate today = LocalDate.now();
		LocalDate latestEnd = membershipRepository.latestMembershipEnd(event.organizationId(), event.clientId(), plan.planId())
				.orElse(null);
		LocalDate startsOn = latestEnd != null && !latestEnd.isBefore(today) ? latestEnd.plusDays(1) : today;
		if ("MEMBERSHIP_PLAN_CHANGED".equals(action)) {
			startsOn = today;
		}
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		LocalDate graceEndsOn = endsOn.plusDays(plan.graceDays());
		UUID membershipId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on,
				    created_at, updated_at, created_by, updated_by, version,
				    source_membership_id, source_payment_event_key
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    :activatedAt, :startsOn, :endsOn, :graceEndsOn,
				    :createdAt, :updatedAt, NULL, NULL, 0,
				    :sourceMembershipId, :sourcePaymentEventKey
				)
				""")
				.param("id", membershipId)
				.param("organizationId", event.organizationId())
				.param("branchId", event.branchId())
				.param("clientId", event.clientId())
				.param("planId", plan.planId())
				.param("saleId", event.saleId())
				.param("activatedAt", now)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("sourceMembershipId", sourceMembershipId)
				.param("sourcePaymentEventKey", event.eventKey())
				.update();
		auditService.recordSystem(event.organizationId(), event.branchId(), null, action, "MEMBERSHIP", membershipId, event.businessContext(), null, event.eventKey());
		MembershipSummary membership = membershipRepository.byId(event.organizationId(), event.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia activada no esta disponible."));
		publishMembershipEvent(event, sourceMembershipId, membership, action);
		return membership;
	}

	private void cancelForPlanChange(PaymentCompletedV1 event, MembershipSummary current) {
		jdbcClient.sql("""
				UPDATE membership
				SET status = 'CANCELLED',
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :membershipId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("updatedAt", OffsetDateTime.now())
				.param("organizationId", event.organizationId())
				.param("branchId", event.branchId())
				.param("membershipId", current.id())
				.update();
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

	private String defaultIdempotencyKey(String operation, UUID membershipId, String provided) {
		return provided == null || provided.isBlank() ? operation + ":" + membershipId : provided.trim();
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
