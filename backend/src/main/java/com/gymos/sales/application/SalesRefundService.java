package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.model.SaleHeader;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundService {
	private final SalesLookupService lookupService;
	private final SalesRefundIdempotencyService idempotencyService;
	private final SalesRefundValidationService validationService;
	private final SalesRefundProcessor processor;

	SalesRefundService(SalesLookupService lookupService, SalesRefundIdempotencyService idempotencyService,
			SalesRefundValidationService validationService, SalesRefundProcessor processor) {
		this.lookupService = lookupService;
		this.idempotencyService = idempotencyService;
		this.validationService = validationService;
		this.processor = processor;
	}

	UUID process(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		lookupService.saleForBranch(principal, saleId);
		validationService.lockSale(principal, saleId);
		SaleHeader sale = lookupService.saleForBranch(principal, saleId);
		var idempotentRefund = idempotencyService.resolve(principal.organizationId(), saleId, request);
		if (idempotentRefund.existingSaleId().isPresent()) {
			return idempotentRefund.existingSaleId().get();
		}
		RefundValidationResult validation = validationService.validate(principal, saleId, sale, request);
		return processor.process(principal, saleId, sale, request, validation,
				idempotentRefund.idempotencyKey(), correlationId);
	}
}
