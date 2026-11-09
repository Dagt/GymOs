package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.api.BusinessException;
import com.gymos.audit.api.AuditService;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.infrastructure.SaleCommandRepository;
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
	private final SalesFinancialIntegrityGuard financialIntegrity;
	private final SaleCommandRepository sales;
	private final AuditService audit;

	SalesPointOfSaleService(
			SaleDetailQuery saleDetailQuery,
			SaleCreationService saleCreationService,
			SalesPaymentService paymentService,
			SalesRefundService refundService,
			SalePaymentCompletionService completionService,
			SalesFinancialIntegrityGuard financialIntegrity,
			SaleCommandRepository sales,
			AuditService audit
	) {
		this.saleDetailQuery = saleDetailQuery;
		this.saleCreationService = saleCreationService;
		this.paymentService = paymentService;
		this.refundService = refundService;
		this.completionService = completionService;
		this.financialIntegrity = financialIntegrity;
		this.sales = sales;
		this.audit = audit;
	}

	@Transactional
	SaleDetailResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		var creation = saleCreationService.create(principal, request, correlationId);
		if (!creation.created()) {
			var existing = detail(principal, creation.saleId());
			if (request.payment() == null) return existing;
			return existing.withOperationPayment(sales.initialPaymentId(principal.organizationId(), creation.saleId()).orElse(null));
		}
		UUID paymentId = null;
		if (request.payment() != null) {
			financialIntegrity.guardAddedPayment(principal, creation.saleId(), request.payment());
			paymentId = paymentService.record(principal, creation.saleId(), request.payment(), correlationId);
			sales.setInitialPayment(principal.organizationId(), creation.saleId(), paymentId);
		}
		completionService.recalculate(principal.organizationId(), creation.saleId(), principal.userId());
		return detail(principal, creation.saleId()).withOperationPayment(paymentId);
	}

	@Transactional
	SaleDetailResult addPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		financialIntegrity.guardAddedPayment(principal, saleId, request);
		UUID paymentId = paymentService.record(principal, saleId, request, correlationId);
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId).withOperationPayment(paymentId);
	}

	@Transactional
	SaleDetailResult uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, paymentService.uploadReceipt(principal, paymentId, file, correlationId));
	}

	@Transactional
	SaleDetailResult validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		financialIntegrity.guardValidatedPayment(principal, paymentId);
		return detail(principal, paymentService.validate(principal, paymentId, correlationId));
	}

	@Transactional
	SaleDetailResult rejectPayment(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		financialIntegrity.guardProviderPaymentRejection(principal, paymentId);
		return detail(principal, paymentService.reject(principal, paymentId, reason, correlationId));
	}

	@Transactional
	SaleDetailResult cancel(GymOsPrincipal principal, UUID saleId, String reason, String correlationId) {
		var sale = financialIntegrity.lockSale(principal, saleId);
		String normalized = SalesIdempotency.blankToNull(reason);
		if (normalized == null || normalized.length() < 3) {
			throw new BusinessException("SALE_CANCELLATION_REASON_REQUIRED", "Indica el motivo de la cancelación.");
		}
		if ("CANCELLED".equals(sale.status())) {
			if (!sales.sameCancellation(principal.organizationId(), principal.branchId(), saleId, normalized)) {
				throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La venta ya fue cancelada con otro motivo.");
			}
			return detail(principal, saleId);
		}
		if (!sales.cancelUnpaidRetailSale(principal.organizationId(), principal.branchId(), saleId, principal.userId(), normalized)) {
			throw new BusinessException("SALE_CANCELLATION_UNAVAILABLE", "Sólo se puede cancelar una venta de mostrador sin pagos pendientes ni confirmados.");
		}
		audit.record(principal, "SALE_CANCELLED", "SALE", saleId, "POS", normalized, correlationId);
		return detail(principal, saleId);
	}

	@Transactional
	SaleDetailResult requestRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		SalesAccessGuard.requireBranch(principal);
		return detail(principal, refundService.process(principal, saleId, request, correlationId));
	}

	private SaleDetailResult detail(GymOsPrincipal principal, UUID saleId) {
		return saleDetailQuery.detailForBranch(principal.organizationId(), principal.branchId(), saleId);
	}
}
