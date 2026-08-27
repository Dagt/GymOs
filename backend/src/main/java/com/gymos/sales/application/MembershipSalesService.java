package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.sales.api.events.PaymentRecordedV1;
import com.gymos.sales.api.events.SaleCreatedV1;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipEnrollmentSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import com.gymos.sales.api.operations.MembershipSaleResult;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository.MembershipSaleRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class MembershipSalesService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private final SalesRepository salesRepository;
	private final MembershipSaleCommandRepository commandRepository;
	private final SalePaymentCompletionService completionService;
	private final PaymentConfigurationOperations paymentConfigurationOperations;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	MembershipSalesService(SalesRepository salesRepository, MembershipSaleCommandRepository commandRepository,
			SalePaymentCompletionService completionService, PaymentConfigurationOperations paymentConfigurationOperations,
			AuditService auditService, ApplicationEventPublisher eventPublisher) {
		this.salesRepository = salesRepository;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.paymentConfigurationOperations = paymentConfigurationOperations;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	MembershipSaleResult renew(CreateMembershipRenewalSaleCommand command) {
		return create(MembershipSaleDraft.forRenewal(command));
	}

	MembershipSaleResult enroll(CreateMembershipEnrollmentSaleCommand command) {
		return create(MembershipSaleDraft.forEnrollment(command));
	}

	MembershipSaleResult changePlan(CreateMembershipPlanChangeSaleCommand command) {
		return create(MembershipSaleDraft.forPlanChange(command));
	}

	private MembershipSaleResult create(MembershipSaleDraft draft) {
		String requestHash = SalesIdempotency.membershipSaleRequestHash(draft.clientId(), draft.sourceMembershipId(), draft.targetPlanId(), draft.productId(),
				draft.registrationFeeCents(), draft.registrationFeeLabel(),
				draft.listPriceCents(), draft.discountCents(), draft.totalCents(), draft.currency(), draft.appliedBenefits(),
				draft.method(), draft.reference(), draft.validateNow(), draft.notes(), draft.businessContext());
		String normalizedKey = SalesIdempotency.blankToNull(draft.idempotencyKey());
		if (normalizedKey != null) {
			var existing = salesRepository.saleHeaderByIdempotencyKey(draft.organizationId(), normalizedKey);
			if (existing.isPresent()) {
				SalesIdempotency.ensureSameRequest(salesRepository.saleRequestHashByIdempotencyKey(draft.organizationId(), normalizedKey).orElse(null), requestHash);
				return toResult(draft.organizationId(), existing.get());
			}
		}
		paymentConfigurationOperations.requireReceptionMethod(draft.organizationId(), draft.method());
		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		String normalizedMethod = draft.method().trim().toUpperCase();
		boolean completed = draft.totalCents() == 0 || draft.validateNow() || "CASH".equals(normalizedMethod);
		String paymentStatus = completed ? "COMPLETED" : "PENDING_VALIDATION";
		String saleStatus = completed ? "PAID" : "PENDING_PAYMENT";
		long paidCents = completed ? draft.totalCents() : 0;
		long balanceCents = completed ? 0 : draft.totalCents();
		String paymentHash = SalesIdempotency.paymentRequestHash(draft.method(), draft.totalCents(), draft.reference(), null, draft.validateNow(), draft.notes());
		boolean created = commandRepository.create(new MembershipSaleRecord(saleId, paymentId, draft.organizationId(), draft.branchId(), draft.clientId(), draft.actorUserId(),
				draft.sourceMembershipId(), draft.targetPlanId(), draft.productId(), draft.planName(), saleNumber, saleStatus, normalizedMethod, paymentStatus,
				draft.planPriceCents(), draft.registrationFeeCents(), draft.registrationFeeLabel(),
				draft.listPriceCents(), draft.discountCents(), draft.totalCents(), paidCents, balanceCents, draft.currency(),
				draft.appliedBenefits(),
				SalesIdempotency.blankToNull(draft.reference()), SalesIdempotency.blankToNull(draft.notes()),
				normalizedKey, requestHash, normalizedKey == null ? null : normalizedKey + ":payment", paymentHash,
				draft.businessContext(), now, completed ? now : null, completed ? draft.actorUserId() : null));
		if (!created) {
			SaleHeader existing = salesRepository.saleHeaderByIdempotencyKey(draft.organizationId(), normalizedKey)
					.orElseThrow(() -> new IllegalStateException("La venta idempotente existe pero no se pudo recuperar."));
			SalesIdempotency.ensureSameRequest(
					salesRepository.saleRequestHashByIdempotencyKey(draft.organizationId(), normalizedKey).orElse(null),
					requestHash
			);
			return toResult(draft.organizationId(), existing);
		}
		auditService.recordSystem(draft.organizationId(), draft.branchId(), draft.actorUserId(), "SALE_CREATED", "SALE", saleId, draft.businessContext(), null, draft.correlationId());
		auditService.recordSystem(draft.organizationId(), draft.branchId(), draft.actorUserId(), "PAYMENT_RECORDED", "PAYMENT", paymentId, draft.businessContext(), null, draft.correlationId());
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, draft.organizationId(), draft.branchId(), saleId, saleNumber, draft.businessContext(), draft.totalCents(), draft.currency(), now));
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, draft.organizationId(), draft.branchId(), saleId, paymentId, normalizedMethod, paymentStatus, draft.totalCents(), draft.currency(), now));
		if (completed) completionService.publishIfNeeded(draft.organizationId(), saleId);
		return toResult(draft.organizationId(), salesRepository.saleHeader(draft.organizationId(), saleId).orElseThrow());
	}

	private MembershipSaleResult toResult(UUID organizationId, SaleHeader sale) {
		UUID paymentId = salesRepository.paymentIdForSale(organizationId, sale.id()).orElse(null);
		return new MembershipSaleResult(sale.id(), sale.saleNumber(), sale.status(), sale.totalCents(), sale.paidCents(), sale.balanceCents(), sale.currency(), "PAID".equals(sale.status()), paymentId);
	}

	private record MembershipSaleDraft(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			UUID clientId,
			UUID sourceMembershipId,
			UUID targetPlanId,
			UUID productId,
			String planName,
			long planPriceCents,
			long registrationFeeCents,
			String registrationFeeLabel,
			long listPriceCents,
			long discountCents,
			long totalCents,
			String currency,
			List<MembershipBenefitSnapshot> appliedBenefits,
			String method,
			String reference,
			boolean validateNow,
			String notes,
			String idempotencyKey,
			String businessContext,
			String correlationId
	) {
		static MembershipSaleDraft forEnrollment(CreateMembershipEnrollmentSaleCommand command) {
			return new MembershipSaleDraft(
					command.organizationId(),
					command.branchId(),
					command.actorUserId(),
					command.clientId(),
					null,
					command.planId(),
					command.productId(),
					command.planName(),
					command.planPriceCents(),
					command.registrationFeeCents(),
					command.registrationFeeLabel(),
					command.listPriceCents(),
					command.discountCents(),
					command.totalCents(),
					command.currency(),
					command.appliedBenefits(),
					command.method(),
					command.reference(),
					command.validateNow(),
					command.notes(),
					command.idempotencyKey(),
					"MEMBERSHIP_ENROLLMENT",
					command.correlationId()
			);
		}

		static MembershipSaleDraft forRenewal(CreateMembershipRenewalSaleCommand command) {
			return new MembershipSaleDraft(
					command.organizationId(),
					command.branchId(),
					command.actorUserId(),
					command.clientId(),
					command.sourceMembershipId(),
					command.planId(),
					command.productId(),
					command.planName(),
					command.planPriceCents(),
					command.registrationFeeCents(),
					command.registrationFeeLabel(),
					command.listPriceCents(),
					command.discountCents(),
					command.totalCents(),
					command.currency(),
					command.appliedBenefits(),
					command.method(),
					command.reference(),
					command.validateNow(),
					command.notes(),
					command.idempotencyKey(),
					"MEMBERSHIP_RENEWAL",
					command.correlationId()
			);
		}

		static MembershipSaleDraft forPlanChange(CreateMembershipPlanChangeSaleCommand command) {
			return new MembershipSaleDraft(
					command.organizationId(),
					command.branchId(),
					command.actorUserId(),
					command.clientId(),
					command.sourceMembershipId(),
					command.targetPlanId(),
					command.productId(),
					command.planName(),
					command.planPriceCents(),
					command.registrationFeeCents(),
					command.registrationFeeLabel(),
					command.listPriceCents(),
					command.discountCents(),
					command.totalCents(),
					command.currency(),
					command.appliedBenefits(),
					command.method(),
					command.reference(),
					command.validateNow(),
					command.notes(),
					command.idempotencyKey(),
					"MEMBERSHIP_PLAN_CHANGE",
					command.correlationId()
			);
		}
	}
}
