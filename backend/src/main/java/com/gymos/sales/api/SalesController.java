package com.gymos.sales.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.SalesService;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import com.gymos.sales.infrastructure.SalesRepository.SaleItemDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	public record SaleDetailResponse(
			SaleHeader sale,
			List<SaleItemDetail> items,
			List<PaymentDetail> payments
	) {
	}
}
