package com.gymos.payments.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.application.MercadoPagoProviderService;
import com.gymos.payments.application.PaymentConfigurationService;
import com.gymos.payments.application.PaymentOperationsHistoryService;
import com.gymos.payments.application.StripeProviderService;
import com.gymos.payments.application.UpdatePaymentConfigurationCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-settings")
public class PaymentConfigurationController {
	private final PaymentConfigurationService service;
	private final StripeProviderService stripeProviderService;
	private final MercadoPagoProviderService mercadoPagoProviderService;
	private final PaymentOperationsHistoryService historyService;

	public PaymentConfigurationController(
			PaymentConfigurationService service,
			StripeProviderService stripeProviderService,
			MercadoPagoProviderService mercadoPagoProviderService,
			PaymentOperationsHistoryService historyService
	) {
		this.service = service;
		this.stripeProviderService = stripeProviderService;
		this.mercadoPagoProviderService = mercadoPagoProviderService;
		this.historyService = historyService;
	}

	@GetMapping("/history/mandates")
	com.gymos.common.api.PageResponse<com.gymos.payments.infrastructure.RecurringBillingRepository.MandateHistoryRecord> mandateHistory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size
	) {
		return historyService.mandates(principal, page, size);
	}

	@GetMapping("/history/recurring-charges")
	com.gymos.common.api.PageResponse<com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeHistoryRecord> recurringChargeHistory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size
	) {
		return historyService.charges(principal, page, size);
	}

	@GetMapping("/history/webhooks")
	com.gymos.common.api.PageResponse<com.gymos.payments.infrastructure.PaymentProviderRepository.WebhookHistoryRecord> webhookHistory(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size
	) {
		return historyService.webhooks(principal, page, size);
	}

	@GetMapping("/operations")
	PaymentOperationsHistoryService.PaymentOperationsView operations(
			@AuthenticationPrincipal GymOsPrincipal principal
	) {
		return historyService.operations(principal);
	}

	@PostMapping("/stripe/connect")
	StripeProviderService.ProviderLink beginStripeOnboarding(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest request
	) {
		return stripeProviderService.beginOnboarding(principal, correlationId(request));
	}

	@PostMapping("/stripe/refresh")
	PaymentConfigurationView refreshStripeConnection(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest request
	) {
		return stripeProviderService.refreshConnection(principal, correlationId(request));
	}

	@PostMapping("/mercado-pago/connect")
	MercadoPagoProviderService.ProviderLink beginMercadoPagoOauth(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest request
	) {
		return mercadoPagoProviderService.beginOauth(principal, correlationId(request));
	}

	@GetMapping("/mercado-pago/oauth/callback")
	ResponseEntity<Void> completeMercadoPagoOauth(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			HttpServletRequest request
	) {
		if (error != null && !error.isBlank()) {
			throw new BusinessException("MERCADO_PAGO_OAUTH_REJECTED", "Mercado Pago no autorizo la vinculacion: " + error);
		}
		String redirect = mercadoPagoProviderService.completeOauth(code, state, correlationId(request));
		return ResponseEntity.status(303).location(URI.create(redirect)).build();
	}

	@GetMapping("/mercado-pago/terminals")
	List<MercadoPagoProviderService.TerminalView> mercadoPagoTerminals(
			@AuthenticationPrincipal GymOsPrincipal principal
	) {
		return mercadoPagoProviderService.terminals(principal);
	}

	@PutMapping("/mercado-pago/terminal")
	PaymentConfigurationView selectMercadoPagoTerminal(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SelectTerminalRequest request,
			HttpServletRequest servletRequest
	) {
		return mercadoPagoProviderService.selectTerminal(principal, request.terminalId(), correlationId(servletRequest));
	}

	@GetMapping
	PaymentConfigurationView configuration(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.configuration(principal);
	}

	@PutMapping
	PaymentConfigurationView update(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody UpdatePaymentConfigurationRequest request,
			HttpServletRequest servletRequest
	) {
		return service.update(principal, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record UpdatePaymentConfigurationRequest(
			boolean receptionManualTerminalEnabled,
			boolean receptionMercadoPagoEnabled,
			@Size(max = 160) String mercadoPagoTerminalId,
			boolean stripeSelfCheckoutEnabled,
			boolean stripeRecurringEnabled
	) {
		private UpdatePaymentConfigurationCommand toCommand() {
			return new UpdatePaymentConfigurationCommand(
					receptionManualTerminalEnabled,
					receptionMercadoPagoEnabled,
					mercadoPagoTerminalId,
					stripeSelfCheckoutEnabled,
					stripeRecurringEnabled
			);
		}
	}

	public record SelectTerminalRequest(@NotBlank @Size(max = 160) String terminalId) {
	}
}
