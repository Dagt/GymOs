package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipEnrollmentSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.CreateVisitPackageSaleCommand;
import com.gymos.sales.api.operations.MembershipSaleResult;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.api.operations.VisitPackageSaleResult;
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
	private final SalesPointOfSaleService pointOfSaleService;
	private final MembershipSalesService membershipSalesService;

	public SalesService(
			SaleDetailQuery saleDetailQuery,
			SalesReportingQuery reportingQuery,
			SalesPointOfSaleService pointOfSaleService,
			MembershipSalesService membershipSalesService
	) {
		this.saleDetailQuery = saleDetailQuery;
		this.reportingQuery = reportingQuery;
		this.pointOfSaleService = pointOfSaleService;
		this.membershipSalesService = membershipSalesService;
	}

	@Transactional(readOnly = true)
	public List<SaleSummary> recent(GymOsPrincipal principal) {
		SalesAccessGuard.requireBranch(principal);
		return saleDetailQuery.recent(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public SalesReportResult report(GymOsPrincipal principal, LocalDate fromDate, LocalDate toDate) {
		SalesAccessGuard.requireBranch(principal);
		return reportingQuery.report(principal.organizationId(), principal.branchId(), fromDate, toDate);
	}

	@Transactional(readOnly = true)
	public SaleDetailResult detail(GymOsPrincipal principal, UUID saleId) {
		SalesAccessGuard.requireBranch(principal);
		return saleDetailQuery.detailForBranch(principal.organizationId(), principal.branchId(), saleId);
	}

	public SaleDetailResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		return pointOfSaleService.create(principal, request, correlationId);
	}

	@Override
	@Transactional
	public MembershipSaleResult createMembershipEnrollment(CreateMembershipEnrollmentSaleCommand command) {
		return membershipSalesService.enroll(command);
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

	@Override
	@Transactional
	public VisitPackageSaleResult createVisitPackagePurchase(CreateVisitPackageSaleCommand command) {
		return membershipSalesService.visitPackage(command);
	}

	public SaleDetailResult addPayment(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		return pointOfSaleService.addPayment(principal, saleId, request, correlationId);
	}

	public SaleDetailResult uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		return pointOfSaleService.uploadReceipt(principal, paymentId, file, correlationId);
	}

	public SaleDetailResult validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		return pointOfSaleService.validatePayment(principal, paymentId, correlationId);
	}

	public SaleDetailResult rejectPayment(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		return pointOfSaleService.rejectPayment(principal, paymentId, reason, correlationId);
	}

	public SaleDetailResult requestRefund(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request, String correlationId) {
		return pointOfSaleService.requestRefund(principal, saleId, request, correlationId);
	}

	public SaleDetailResult cancel(GymOsPrincipal principal, UUID saleId, String reason, String correlationId) {
		return pointOfSaleService.cancel(principal, saleId, reason, correlationId);
	}
}
