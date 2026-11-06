package com.gymos.sales.api;

import com.gymos.clients.api.operations.ClientBranchAccess;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.SalesService;
import com.gymos.sales.application.SalesProviderPaymentService;
import com.gymos.payments.api.MercadoPagoPointOperations;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RefundItemCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.application.command.SaleItemCommand;
import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.application.result.SalesReportResult;
import com.gymos.sales.model.AppliedBenefitDetail;
import com.gymos.sales.model.MembershipDetail;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.PaymentMethodBreakdown;
import com.gymos.sales.model.PaymentReportTotals;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import com.gymos.sales.model.SaleBusinessContextBreakdown;
import com.gymos.sales.model.SaleItemDetail;
import com.gymos.sales.model.SaleStatusBreakdown;
import com.gymos.sales.model.SaleSummary;
import com.gymos.sales.model.SalesReportTotals;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
	private final SalesService salesService;
	private final SalesProviderPaymentService providerPaymentService;
	private final ClientBranchAccess clients;

	public SalesController(SalesService salesService, SalesProviderPaymentService providerPaymentService, ClientBranchAccess clients) {
		this.salesService = salesService;
		this.providerPaymentService = providerPaymentService;
		this.clients = clients;
	}

	@PostMapping("/payments/{paymentId}/mercado-pago-order")
	MercadoPagoPointOperations.PointOrderResult createMercadoPagoOrder(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId
	) {
		return providerPaymentService.createMercadoPagoOrder(principal, paymentId);
	}

	@GetMapping
	List<SaleSummary> recent(@AuthenticationPrincipal GymOsPrincipal principal) {
		return salesService.recent(principal);
	}

	@GetMapping("/reports/summary")
	SalesReportResponse report(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
	) {
		return toResponse(salesService.report(principal, fromDate, toDate));
	}

	@PostMapping
	SaleDetailResponse create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateSaleRequest request,
			HttpServletRequest servletRequest
	) {
		if (request.clientId() != null) {
			clients.requireVisible(principal, request.clientId());
		}
		return toResponse(salesService.create(principal, request.toCommand(), correlationId(servletRequest)));
	}

	@GetMapping("/{saleId}")
	SaleDetailResponse detail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID saleId) {
		return toResponse(salesService.detail(principal, saleId));
	}

	@PostMapping("/{saleId}/payments")
	SaleDetailResponse addPayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID saleId,
			@Valid @RequestBody ManualPaymentRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(salesService.addPayment(principal, saleId, request.toCommand(), correlationId(servletRequest)));
	}

	@PostMapping("/payments/{paymentId}/validate")
	SaleDetailResponse validatePayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			HttpServletRequest servletRequest
	) {
		return toResponse(salesService.validatePayment(principal, paymentId, correlationId(servletRequest)));
	}

	@PostMapping("/payments/{paymentId}/reject")
	SaleDetailResponse rejectPayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			@Valid @RequestBody RejectPaymentRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(salesService.rejectPayment(principal, paymentId, request.reason(), correlationId(servletRequest)));
	}

	@PostMapping(value = "/payments/{paymentId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	SaleDetailResponse uploadReceipt(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest servletRequest
	) {
		return toResponse(salesService.uploadReceipt(principal, paymentId, file, correlationId(servletRequest)));
	}

	@PostMapping("/{saleId}/refunds")
	SaleDetailResponse requestRefund(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID saleId,
			@Valid @RequestBody RefundRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(salesService.requestRefund(principal, saleId, request.toCommand(), correlationId(servletRequest)));
	}

	private SaleDetailResponse toResponse(SaleDetailResult result) {
		return new SaleDetailResponse(
				result.sale(),
				result.items(),
				result.appliedBenefits(),
				result.payments(),
				result.refunds(),
				result.memberships()
		);
	}

	private SalesReportResponse toResponse(SalesReportResult result) {
		return new SalesReportResponse(
				result.fromDate(),
				result.toDate(),
				result.currency(),
				result.sales(),
				result.payments(),
				result.salesByStatus(),
				result.salesByBusinessContext(),
				result.paymentsByMethod(),
				result.recentSales()
		);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateSaleRequest(
			UUID clientId,
			@NotEmpty List<@Valid SaleItemRequest> items,
			@Min(0) long discountCents,
			@Size(max = 2000) String notes,
			@Valid ManualPaymentRequest payment,
			@Size(max = 160) String idempotencyKey
	) {
		private CreateSaleCommand toCommand() {
			return new CreateSaleCommand(
					clientId,
					items.stream().map(SaleItemRequest::toCommand).toList(),
					discountCents,
					notes,
					payment == null ? null : payment.toCommand(),
					idempotencyKey
			);
		}
	}

	public record SaleItemRequest(
			@NotNull UUID productId,
			@Min(1) int quantity,
			@Min(0) long discountCents
	) {
		private SaleItemCommand toCommand() {
			return new SaleItemCommand(productId, quantity, discountCents);
		}
	}

	public record ManualPaymentRequest(
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL|CARD_MERCADO_PAGO") String method,
			@Min(1) long amountCents,
			@Size(max = 160) String reference,
			@Size(max = 512) String receiptObjectKey,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		private ManualPaymentCommand toCommand() {
			return new ManualPaymentCommand(method, amountCents, reference, receiptObjectKey, validateNow, notes, idempotencyKey);
		}
	}

	public record RejectPaymentRequest(
			@NotNull @Size(min = 3, max = 500) String reason
	) {
	}

	public record RefundRequest(
			UUID paymentId,
			@Min(1) long amountCents,
			@NotNull @Size(min = 3, max = 500) String reason,
			@Size(max = 160) String idempotencyKey,
			List<@Valid RefundItemRequest> items
	) {
		private RequestRefundCommand toCommand() {
			return new RequestRefundCommand(
					paymentId,
					amountCents,
					reason,
					idempotencyKey,
					items == null ? List.of() : items.stream().map(RefundItemRequest::toCommand).toList()
			);
		}
	}

	public record RefundItemRequest(
			@NotNull UUID saleItemId,
			@Min(1) int quantity,
			boolean restock
	) {
		private RefundItemCommand toCommand() {
			return new RefundItemCommand(saleItemId, quantity, restock);
		}
	}

	public record SaleDetailResponse(
			SaleHeader sale,
			List<SaleItemDetail> items,
			List<AppliedBenefitDetail> appliedBenefits,
			List<PaymentDetail> payments,
			List<RefundDetail> refunds,
			List<MembershipDetail> memberships
	) {
	}

	public record SalesReportResponse(
			LocalDate fromDate,
			LocalDate toDate,
			String currency,
			SalesReportTotals sales,
			PaymentReportTotals payments,
			List<SaleStatusBreakdown> salesByStatus,
			List<SaleBusinessContextBreakdown> salesByBusinessContext,
			List<PaymentMethodBreakdown> paymentsByMethod,
			List<SaleSummary> recentSales
	) {
	}
}
