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
		return create(command.organizationId(), command.branchId(), command.actorUserId(), command.clientId(), command.sourceMembershipId(),
				command.planId(), command.productId(), command.planName(), command.priceCents(), command.currency(), command.method(),
				command.reference(), command.validateNow(), command.notes(), command.idempotencyKey(), "MEMBERSHIP_RENEWAL", command.correlationId());
	}

	MembershipSaleResult changePlan(CreateMembershipPlanChangeSaleCommand command) {
		return create(command.organizationId(), command.branchId(), command.actorUserId(), command.clientId(), command.sourceMembershipId(),
				command.targetPlanId(), command.productId(), command.planName(), command.priceCents(), command.currency(), command.method(),
				command.reference(), command.validateNow(), command.notes(), command.idempotencyKey(), "MEMBERSHIP_PLAN_CHANGE", command.correlationId());
	}

	private MembershipSaleResult create(UUID organizationId, UUID branchId, UUID actorUserId, UUID clientId,
			UUID sourceMembershipId, UUID targetPlanId, UUID productId, String planName, long priceCents,
			String currency, String method, String reference, boolean validateNow, String notes,
			String idempotencyKey, String businessContext, String correlationId) {
		String requestHash = SalesIdempotency.membershipSaleRequestHash(clientId, sourceMembershipId, targetPlanId, productId,
				priceCents, currency, method, reference, validateNow, notes, businessContext);
		String normalizedKey = SalesIdempotency.blankToNull(idempotencyKey);
		if (normalizedKey != null) {
			var existing = salesRepository.saleHeaderByIdempotencyKey(organizationId, normalizedKey);
			if (existing.isPresent()) {
				SalesIdempotency.ensureSameRequest(salesRepository.saleRequestHashByIdempotencyKey(organizationId, normalizedKey).orElse(null), requestHash);
				return toResult(existing.get());
			}
		}
		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		String normalizedMethod = method.trim().toUpperCase();
		boolean completed = validateNow || "CASH".equals(normalizedMethod);
		String paymentStatus = completed ? "COMPLETED" : "PENDING_VALIDATION";
		String saleStatus = completed ? "PAID" : "PENDING_PAYMENT";
		long paidCents = completed ? priceCents : 0;
		long balanceCents = completed ? 0 : priceCents;
		String paymentHash = SalesIdempotency.paymentRequestHash(method, priceCents, reference, null, validateNow, notes);
		commandRepository.create(new MembershipSaleRecord(saleId, paymentId, organizationId, branchId, clientId, actorUserId,
				sourceMembershipId, targetPlanId, productId, planName, saleNumber, saleStatus, normalizedMethod, paymentStatus,
				priceCents, paidCents, balanceCents, currency, SalesIdempotency.blankToNull(reference), SalesIdempotency.blankToNull(notes),
				normalizedKey, requestHash, normalizedKey == null ? null : normalizedKey + ":payment", paymentHash,
				businessContext, now, completed ? now : null, completed ? actorUserId : null));
		auditService.recordSystem(organizationId, branchId, actorUserId, "SALE_CREATED", "SALE", saleId, businessContext, null, correlationId);
		auditService.recordSystem(organizationId, branchId, actorUserId, "PAYMENT_RECORDED", "PAYMENT", paymentId, businessContext, null, correlationId);
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, organizationId, branchId, saleId, saleNumber, businessContext, priceCents, currency, now));
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, organizationId, branchId, saleId, paymentId, normalizedMethod, paymentStatus, priceCents, currency, now));
		if (completed) completionService.publishIfNeeded(organizationId, saleId);
		return toResult(salesRepository.saleHeader(organizationId, saleId).orElseThrow());
	}

	private MembershipSaleResult toResult(SaleHeader sale) {
		return new MembershipSaleResult(sale.id(), sale.saleNumber(), sale.status(), sale.totalCents(), sale.paidCents(), sale.balanceCents(), sale.currency(), "PAID".equals(sale.status()));
	}
}
