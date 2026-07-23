package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.application.result.SaleDetailResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class SalesPointOfSaleService {
	private final SaleDetailQuery saleDetailQuery;
	private final SaleCreationService saleCreationService;
	private final SalesPaymentService paymentService;
	private final SalesRefundService refundService;
	private final SalePaymentCompletionService completionService;

	SalesPointOfSaleService(
			SaleDetailQuery saleDetailQuery,
			SaleCreationService saleCreationService,
			SalesPaymentService paymentService,
			SalesRefundService refundService,
			SalePaymentCompletionService completionService
	) {
		this.saleDetailQuery = saleDetailQuery;
		this.saleCreationService = saleCreationService;
		this.paymentService = paymentService;
		this.refundService = refundService;
		this.completionService = completionService;
	}

	@Transactional
	SaleDetailResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		var creation = saleCreationService.create(principal, request, correlationId);
		if (!creation.created()) {
			return detail(principal, creation.saleId());
		}
		if (request.payment() != null) {
			paymentService.record(principal, creation.saleId(), request.payment(), correlationId);
		}
		completionService.recalculate(principal.organizationId(), creation.saleId(), principal.userId());
		return detail(principal, creation.saleId());
	}

	@Transactional
	SaleDetailResult addPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		paymentService.record(principal, saleId, request, correlationId);
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	SaleDetailResult uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, paymentService.uploadReceipt(principal, paymentId, file, correlationId));
	}

	@Transactional
	SaleDetailResult validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, paymentService.validate(principal, paymentId, correlationId));
	}

	@Transactional
	SaleDetailResult rejectPayment(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, paymentService.reject(principal, paymentId, reason, correlationId));
	}

	@Transactional
	SaleDetailResult requestRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, refundService.process(principal, saleId, request, correlationId));
	}

	private SaleDetailResult detail(GymOsPrincipal principal, UUID saleId) {
		return saleDetailQuery.detail(principal.organizationId(), saleId);
	}
}
