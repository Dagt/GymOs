package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.MembershipSaleResult;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.application.result.SalesReportResult;
import com.gymos.sales.model.SaleSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalesService implements SalesOperations {
	private final SaleDetailQuery saleDetailQuery;
	private final SalesReportingQuery reportingQuery;
	private final SaleCreationService saleCreationService;
	private final SalesPaymentService paymentService;
	private final SalesRefundService refundService;
	private final MembershipSalesService membershipSalesService;
	private final SalePaymentCompletionService completionService;

	public SalesService(
			SaleDetailQuery saleDetailQuery,
			SalesReportingQuery reportingQuery,
			SaleCreationService saleCreationService,
			SalesPaymentService paymentService,
			SalesRefundService refundService,
			MembershipSalesService membershipSalesService,
			SalePaymentCompletionService completionService
	) {
		this.saleDetailQuery = saleDetailQuery;
		this.reportingQuery = reportingQuery;
		this.saleCreationService = saleCreationService;
		this.paymentService = paymentService;
		this.refundService = refundService;
		this.membershipSalesService = membershipSalesService;
		this.completionService = completionService;
	}

	@Transactional(readOnly = true)
	public List<SaleSummary> recent(GymOsPrincipal principal) {
		requireBranch(principal);
		return saleDetailQuery.recent(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public SalesReportResult report(GymOsPrincipal principal, LocalDate fromDate, LocalDate toDate) {
		requireBranch(principal);
		return reportingQuery.report(principal.organizationId(), principal.branchId(), fromDate, toDate);
	}

	@Transactional(readOnly = true)
	public SaleDetailResult detail(GymOsPrincipal principal, UUID saleId) {
		requirePrincipal(principal);
		return saleDetailQuery.detail(principal.organizationId(), saleId);
	}

	@Transactional
	public SaleDetailResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		requireBranch(principal);
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

	@Override
	@Transactional
	public MembershipSaleResult createMembershipRenewal(CreateMembershipRenewalSaleCommand command) {
		return membershipSalesService.renew(command);
	}

	@Override
	@Transactional
	public MembershipSaleResult createMembershipPlanChange(CreateMembershipPlanChangeSaleCommand command) {
		return membershipSalesService.changePlan(command);
	}

	@Transactional
	public SaleDetailResult addPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		requireBranch(principal);
		paymentService.record(principal, saleId, request, correlationId);
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResult uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		requireBranch(principal);
		return detail(principal, paymentService.uploadReceipt(principal, paymentId, file, correlationId));
	}

	@Transactional
	public SaleDetailResult validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		requireBranch(principal);
		return detail(principal, paymentService.validate(principal, paymentId, correlationId));
	}

	@Transactional
	public SaleDetailResult rejectPayment(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		requireBranch(principal);
		return detail(principal, paymentService.reject(principal, paymentId, reason, correlationId));
	}

	@Transactional
	public SaleDetailResult requestRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		requireBranch(principal);
		return detail(principal, refundService.process(principal, saleId, request, correlationId));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private void requireBranch(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
