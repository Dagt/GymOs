package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundProcessor {
	private final SalesRefundCommandRepository commandRepository;
	private final AuditService auditService;

	SalesRefundProcessor(SalesRefundCommandRepository commandRepository, AuditService auditService) {
		this.commandRepository = commandRepository;
		this.auditService = auditService;
	}

	UUID process(GymOsPrincipal principal, UUID saleId, SaleHeader sale, RequestRefundCommand request,
			String idempotencyKey, String correlationId) {
		UUID refundId = commandRepository.createProcessed(
				principal.organizationId(), principal.branchId(), saleId, request.paymentId(), request.amountCents(),
				sale.currency(), request.reason(), idempotencyKey, principal.userId()
		);
		auditService.record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", request.reason(), correlationId);
		return saleId;
	}
}
