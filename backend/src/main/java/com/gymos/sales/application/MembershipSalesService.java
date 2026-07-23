package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.sales.api.events.PaymentRecordedV1;
import com.gymos.sales.api.events.SaleCreatedV1;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.MembershipSaleResult;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository.MembershipSaleRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class MembershipSalesService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private final SalesRepository salesRepository;
	private final MembershipSaleCommandRepository commandRepository;
	private final SalePaymentCompletionService completionService;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	MembershipSalesService(SalesRepository salesRepository, MembershipSaleCommandRepository commandRepository,
			SalePaymentCompletionService completionService, AuditService auditService, ApplicationEventPublisher eventPublisher) {
		this.salesRepository = salesRepository;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	MembershipSaleResult renew(CreateMembershipRenewalSaleCommand command) {
		return create(MembershipSaleDraft.forRenewal(command));
	}

	MembershipSaleResult changePlan(CreateMembershipPlanChangeSaleCommand command) {
		return create(MembershipSaleDraft.forPlanChange(command));
	}

	private MembershipSaleResult create(MembershipSaleDraft draft) {
		String requestHash = SalesIdempotency.membershipSaleRequestHash(draft.clientId(), draft.sourceMembershipId(), draft.targetPlanId(), draft.productId(),
				draft.priceCents(), draft.currency(), draft.method(), draft.reference(), draft.validateNow(), draft.notes(), draft.businessContext());
		String normalizedKey = SalesIdempotency.blankToNull(draft.idempotencyKey());
		if (normalizedKey != null) {
			var existing = salesRepository.saleHeaderByIdempotencyKey(draft.organizationId(), normalizedKey);
			if (existing.isPresent()) {
				SalesIdempotency.ensureSameRequest(salesRepository.saleRequestHashByIdempotencyKey(draft.organizationId(), normalizedKey).orElse(null), requestHash);
				return toResult(existing.get());
			}
		}
		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		String normalizedMethod = draft.method().trim().toUpperCase();
		boolean completed = draft.validateNow() || "CASH".equals(normalizedMethod);
		String paymentStatus = completed ? "COMPLETED" : "PENDING_VALIDATION";
		String saleStatus = completed ? "PAID" : "PENDING_PAYMENT";
		long paidCents = completed ? draft.priceCents() : 0;
		long balanceCents = completed ? 0 : draft.priceCents();
		String paymentHash = SalesIdempotency.paymentRequestHash(draft.method(), draft.priceCents(), draft.reference(), null, draft.validateNow(), draft.notes());
		commandRepository.create(new MembershipSaleRecord(saleId, paymentId, draft.organizationId(), draft.branchId(), draft.clientId(), draft.actorUserId(),
				draft.sourceMembershipId(), draft.targetPlanId(), draft.productId(), draft.planName(), saleNumber, saleStatus, normalizedMethod, paymentStatus,
				draft.priceCents(), paidCents, balanceCents, draft.currency(), SalesIdempotency.blankToNull(draft.reference()), SalesIdempotency.blankToNull(draft.notes()),
				normalizedKey, requestHash, normalizedKey == null ? null : normalizedKey + ":payment", paymentHash,
				draft.businessContext(), now, completed ? now : null, completed ? draft.actorUserId() : null));
		auditService.recordSystem(draft.organizationId(), draft.branchId(), draft.actorUserId(), "SALE_CREATED", "SALE", saleId, draft.businessContext(), null, draft.correlationId());
		auditService.recordSystem(draft.organizationId(), draft.branchId(), draft.actorUserId(), "PAYMENT_RECORDED", "PAYMENT", paymentId, draft.businessContext(), null, draft.correlationId());
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, draft.organizationId(), draft.branchId(), saleId, saleNumber, draft.businessContext(), draft.priceCents(), draft.currency(), now));
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, draft.organizationId(), draft.branchId(), saleId, paymentId, normalizedMethod, paymentStatus, draft.priceCents(), draft.currency(), now));
		if (completed) completionService.publishIfNeeded(draft.organizationId(), saleId);
		return toResult(salesRepository.saleHeader(draft.organizationId(), saleId).orElseThrow());
	}

	private MembershipSaleResult toResult(SaleHeader sale) {
		return new MembershipSaleResult(sale.id(), sale.saleNumber(), sale.status(), sale.totalCents(), sale.paidCents(), sale.balanceCents(), sale.currency(), "PAID".equals(sale.status()));
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
			long priceCents,
			String currency,
			String method,
			String reference,
			boolean validateNow,
			String notes,
			String idempotencyKey,
			String businessContext,
			String correlationId
	) {
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
					command.priceCents(),
					command.currency(),
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
					command.priceCents(),
					command.currency(),
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
