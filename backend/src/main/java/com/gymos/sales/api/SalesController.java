package com.gymos.sales.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.SalesService;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.PaymentMethodBreakdown;
import com.gymos.sales.infrastructure.SalesRepository.PaymentReportTotals;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import com.gymos.sales.infrastructure.SalesRepository.SaleItemDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleSummary;
import com.gymos.sales.infrastructure.SalesRepository.MembershipDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleStatusBreakdown;
import com.gymos.sales.infrastructure.SalesRepository.SalesReportTotals;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
	private final SalesService salesService;

	public SalesController(SalesService salesService) {
		this.salesService = salesService;
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
		return salesService.report(principal, fromDate, toDate);
	}

	@PostMapping
	SaleDetailResponse create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateSaleRequest request,
			HttpServletRequest servletRequest
	) {
		return salesService.create(principal, request, correlationId(servletRequest));
	}

	@GetMapping("/{saleId}")
	SaleDetailResponse detail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID saleId) {
		return salesService.detail(principal, saleId);
	}

	@PostMapping("/{saleId}/payments")
	SaleDetailResponse addPayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID saleId,
			@Valid @RequestBody ManualPaymentRequest request,
			HttpServletRequest servletRequest
	) {
		return salesService.addPayment(principal, saleId, request, correlationId(servletRequest));
	}

	@PostMapping("/payments/{paymentId}/validate")
	SaleDetailResponse validatePayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			HttpServletRequest servletRequest
	) {
		return salesService.validatePayment(principal, paymentId, correlationId(servletRequest));
	}

	@PostMapping("/payments/{paymentId}/reject")
	SaleDetailResponse rejectPayment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			@Valid @RequestBody RejectPaymentRequest request,
			HttpServletRequest servletRequest
	) {
		return salesService.rejectPayment(principal, paymentId, request.reason(), correlationId(servletRequest));
	}

	@PostMapping(value = "/payments/{paymentId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	SaleDetailResponse uploadReceipt(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID paymentId,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest servletRequest
	) {
		return salesService.uploadReceipt(principal, paymentId, file, correlationId(servletRequest));
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
			@Valid ManualPaymentRequest payment
	) {
	}

	public record SaleItemRequest(
			@NotNull UUID productId,
			@Min(1) int quantity,
			@Min(0) long discountCents
	) {
	}

	public record ManualPaymentRequest(
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method,
			@Min(1) long amountCents,
			@Size(max = 160) String reference,
			@Size(max = 512) String receiptObjectKey,
			boolean validateNow,
			@Size(max = 2000) String notes
	) {
	}

	public record RejectPaymentRequest(
			@NotNull @Size(min = 3, max = 500) String reason
	) {
	}

	public record SaleDetailResponse(
			SaleHeader sale,
			List<SaleItemDetail> items,
			List<PaymentDetail> payments,
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
			List<PaymentMethodBreakdown> paymentsByMethod,
			List<SaleSummary> recentSales
	) {
	}
}
