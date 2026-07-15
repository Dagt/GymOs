package com.gymos.memberships.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.MembershipController.ChangePlanRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipResponse;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final JdbcClient jdbcClient;
	private final MembershipRepository membershipRepository;
	private final AuditService auditService;

	public MembershipService(
			JdbcClient jdbcClient,
			MembershipRepository membershipRepository,
			AuditService auditService
	) {
		this.jdbcClient = jdbcClient;
		this.membershipRepository = membershipRepository;
		this.auditService = auditService;
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
	public RenewMembershipResponse renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipRequest request,
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

		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID saleItemId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		String method = request.method().trim().toUpperCase();
		boolean completed = request.validateNow() || "CASH".equals(method);
		String paymentStatus = completed ? "COMPLETED" : "PENDING_VALIDATION";
		String saleStatus = completed ? "PAID" : "PENDING_PAYMENT";
		long paidCents = completed ? plan.priceCents() : 0;
		long balanceCents = completed ? 0 : plan.priceCents();

		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, :status,
				    :subtotalCents, 0, :totalCents, :paidCents, :balanceCents,
				    :currency, :notes, :soldAt, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", saleId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", current.clientId())
				.param("saleNumber", saleNumber)
				.param("status", saleStatus)
				.param("subtotalCents", plan.priceCents())
				.param("totalCents", plan.priceCents())
				.param("paidCents", paidCents)
				.param("balanceCents", balanceCents)
				.param("currency", plan.currency())
				.param("notes", blankToNull(request.notes()))
				.param("soldAt", now)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();

		jdbcClient.sql("""
				INSERT INTO sale_item (
				    id, sale_id, organization_id, product_id, item_type, name, quantity,
				    unit_price_cents, discount_cents, total_cents, created_at
				)
				VALUES (
				    :id, :saleId, :organizationId, :productId, 'MEMBERSHIP_PLAN', :name, 1,
				    :unitPriceCents, 0, :totalCents, :createdAt
				)
				""")
				.param("id", saleItemId)
				.param("saleId", saleId)
				.param("organizationId", principal.organizationId())
				.param("productId", plan.productId())
				.param("name", plan.planName())
				.param("unitPriceCents", plan.priceCents())
				.param("totalCents", plan.priceCents())
				.param("createdAt", now)
				.update();

		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :amountCents, :currency, :reference, NULL, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", paymentId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("saleId", saleId)
				.param("clientId", current.clientId())
				.param("method", method)
				.param("status", paymentStatus)
				.param("amountCents", plan.priceCents())
				.param("currency", plan.currency())
				.param("reference", blankToNull(request.reference()))
				.param("receivedAt", now)
				.param("validatedAt", completed ? now : null)
				.param("validatedBy", completed ? principal.userId() : null)
				.param("notes", blankToNull(request.notes()))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();

		auditService.record(principal, "SALE_CREATED", "SALE", saleId, "MEMBERSHIP_RENEWAL", null, correlationId);
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "MEMBERSHIP_RENEWAL", null, correlationId);

		MembershipSummary renewed = null;
		if (completed) {
			renewed = activateRenewal(principal, current, plan, saleId, now, correlationId);
		}

		return new RenewMembershipResponse(
				saleId,
				saleNumber,
				saleStatus,
				plan.priceCents(),
				paidCents,
				balanceCents,
				plan.currency(),
				renewed
		);
	}

	@Transactional
	public RenewMembershipResponse changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangePlanRequest request,
			String correlationId
	) {
		requireBranch(principal);
		MembershipSummary current = membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_CHANGEABLE", "Solo puedes cambiar una membresia activa.");
		}
		if (current.planId().equals(request.targetPlanId())) {
			throw new BusinessException("MEMBERSHIP_PLAN_UNCHANGED", "Selecciona un plan diferente.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), request.targetPlanId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan seleccionado no esta disponible."));
		String method = request.method().trim().toUpperCase();
		if (!request.validateNow() && !"CASH".equals(method)) {
			throw new BusinessException("PLAN_CHANGE_REQUIRES_COMPLETED_PAYMENT", "El cambio de plan requiere pago completado.");
		}

		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID saleItemId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();

		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PAID',
				    :subtotalCents, 0, :totalCents, :paidCents, 0,
				    :currency, :notes, :soldAt, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", saleId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", current.clientId())
				.param("saleNumber", saleNumber)
				.param("subtotalCents", plan.priceCents())
				.param("totalCents", plan.priceCents())
				.param("paidCents", plan.priceCents())
				.param("currency", plan.currency())
				.param("notes", blankToNull(request.notes()))
				.param("soldAt", now)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();

		jdbcClient.sql("""
				INSERT INTO sale_item (
				    id, sale_id, organization_id, product_id, item_type, name, quantity,
				    unit_price_cents, discount_cents, total_cents, created_at
				)
				VALUES (
				    :id, :saleId, :organizationId, :productId, 'MEMBERSHIP_PLAN', :name, 1,
				    :unitPriceCents, 0, :totalCents, :createdAt
				)
				""")
				.param("id", saleItemId)
				.param("saleId", saleId)
				.param("organizationId", principal.organizationId())
				.param("productId", plan.productId())
				.param("name", plan.planName())
				.param("unitPriceCents", plan.priceCents())
				.param("totalCents", plan.priceCents())
				.param("createdAt", now)
				.update();

		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, 'COMPLETED',
				    :amountCents, :currency, :reference, NULL, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", paymentId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("saleId", saleId)
				.param("clientId", current.clientId())
				.param("method", method)
				.param("amountCents", plan.priceCents())
				.param("currency", plan.currency())
				.param("reference", blankToNull(request.reference()))
				.param("receivedAt", now)
				.param("validatedAt", now)
				.param("validatedBy", principal.userId())
				.param("notes", blankToNull(request.notes()))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();

		jdbcClient.sql("""
				UPDATE membership
				SET status = 'CANCELLED',
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :membershipId
				  AND deleted_at IS NULL
				""")
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("membershipId", current.id())
				.update();

		MembershipSummary changed = activatePlanChange(principal, current, plan, saleId, now, correlationId);
		auditService.record(principal, "SALE_CREATED", "SALE", saleId, "MEMBERSHIP_PLAN_CHANGE", null, correlationId);
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "MEMBERSHIP_PLAN_CHANGE", null, correlationId);
		auditService.record(principal, "MEMBERSHIP_PLAN_CHANGED", "MEMBERSHIP", changed.id(), "MEMBERSHIP_PLAN_CHANGE", null, correlationId);

		return new RenewMembershipResponse(
				saleId,
				saleNumber,
				"PAID",
				plan.priceCents(),
				plan.priceCents(),
				0,
				plan.currency(),
				changed
		);
	}

	private MembershipSummary activateRenewal(
			GymOsPrincipal principal,
			MembershipSummary current,
			PlanForRenewal plan,
			UUID saleId,
			OffsetDateTime now,
			String correlationId
	) {
		LocalDate today = LocalDate.now();
		LocalDate latestEnd = membershipRepository.latestMembershipEnd(principal.organizationId(), current.clientId(), plan.planId())
				.orElse(null);
		LocalDate startsOn = latestEnd != null && !latestEnd.isBefore(today) ? latestEnd.plusDays(1) : today;
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		LocalDate graceEndsOn = endsOn.plusDays(plan.graceDays());
		UUID renewedMembershipId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    :activatedAt, :startsOn, :endsOn, :graceEndsOn,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", renewedMembershipId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", current.clientId())
				.param("planId", plan.planId())
				.param("saleId", saleId)
				.param("activatedAt", now)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "MEMBERSHIP_RENEWED", "MEMBERSHIP", renewedMembershipId, "MEMBERSHIP_RENEWAL", null, correlationId);
		return membershipRepository.byId(principal.organizationId(), principal.branchId(), renewedMembershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia renovada no esta disponible."));
	}

	private MembershipSummary activatePlanChange(
			GymOsPrincipal principal,
			MembershipSummary current,
			PlanForRenewal plan,
			UUID saleId,
			OffsetDateTime now,
			String correlationId
	) {
		LocalDate startsOn = LocalDate.now();
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		LocalDate graceEndsOn = endsOn.plusDays(plan.graceDays());
		UUID changedMembershipId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    :activatedAt, :startsOn, :endsOn, :graceEndsOn,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", changedMembershipId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", current.clientId())
				.param("planId", plan.planId())
				.param("saleId", saleId)
				.param("activatedAt", now)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "MEMBERSHIP_CANCELLED_BY_PLAN_CHANGE", "MEMBERSHIP", current.id(), "MEMBERSHIP_PLAN_CHANGE", null, correlationId);
		return membershipRepository.byId(principal.organizationId(), principal.branchId(), changedMembershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia de cambio de plan no esta disponible."));
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
