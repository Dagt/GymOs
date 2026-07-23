package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.RefundProcessedV1;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.model.SaleHeader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SalesRefundProcessor {
	private final SalesRefundCommandRepository commandRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final AuditService auditService;

	SalesRefundProcessor(SalesRefundCommandRepository commandRepository,
			ApplicationEventPublisher eventPublisher, AuditService auditService) {
		this.commandRepository = commandRepository;
		this.eventPublisher = eventPublisher;
		this.auditService = auditService;
	}

	UUID process(GymOsPrincipal principal, UUID saleId, SaleHeader sale, RequestRefundCommand request,
			long alreadyRefunded, String idempotencyKey, String correlationId) {
		UUID refundId = commandRepository.createProcessed(
				principal.organizationId(), principal.branchId(), saleId, request.paymentId(), request.amountCents(),
				sale.currency(), request.reason(), idempotencyKey, principal.userId()
		);
		if (alreadyRefunded + request.amountCents() == sale.paidCents()) {
			eventPublisher.publishEvent(new RefundProcessedV1(
					principal.organizationId(), principal.branchId(), saleId, refundId, principal.userId(),
					OffsetDateTime.now(), safeCorrelationId(correlationId)
			));
		}
		auditService.record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", request.reason(), correlationId);
		return saleId;
	}

	private UUID safeCorrelationId(String correlationId) {
		try { return UUID.fromString(correlationId); }
		catch (RuntimeException exception) {
			return UUID.nameUUIDFromBytes(("gymos:" + correlationId).getBytes(StandardCharsets.UTF_8));
		}
	}
}
