package com.gymos.payments.api;

import com.gymos.payments.application.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-webhooks")
public class PaymentWebhookController {
	private final PaymentWebhookService service;

	public PaymentWebhookController(PaymentWebhookService service) {
		this.service = service;
	}

	@PostMapping("/stripe")
	ResponseEntity<Void> stripe(
			@RequestBody String rawBody,
			@RequestHeader("Stripe-Signature") String signature
	) {
		service.stripe(rawBody, signature);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/mercado-pago")
	ResponseEntity<Void> mercadoPago(
			@RequestBody(required = false) String rawBody,
			@RequestHeader("x-signature") String signature,
			@RequestHeader("x-request-id") String requestId,
			@RequestParam(name = "data.id") String dataId,
			@RequestParam(required = false) String id,
			@RequestParam(required = false) String type
	) {
		service.mercadoPago(rawBody == null ? "" : rawBody, signature, requestId, dataId, id, type);
		return ResponseEntity.ok().build();
	}
}
