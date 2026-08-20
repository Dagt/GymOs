package com.gymos.payments.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.payment-providers")
public record PaymentProviderProperties(
		String credentialKey,
		String frontendBaseUrl,
		Stripe stripe,
		MercadoPago mercadoPago
) {
	public PaymentProviderProperties {
		frontendBaseUrl = blankDefault(frontendBaseUrl, "http://localhost:3000");
		stripe = stripe == null ? new Stripe(null, null, null, null, null) : stripe;
		mercadoPago = mercadoPago == null ? new MercadoPago(null, null, null, null, null, null) : mercadoPago;
	}

	public record Stripe(
			String secretKey,
			String webhookSecret,
			String returnUrl,
			String refreshUrl,
			String apiBaseUrl
	) {
		public Stripe {
			apiBaseUrl = blankDefault(apiBaseUrl, "https://api.stripe.com");
		}
	}

	public record MercadoPago(
			String clientId,
			String clientSecret,
			String redirectUri,
			String webhookSecret,
			String apiBaseUrl,
			String authorizationBaseUrl
	) {
		public MercadoPago {
			apiBaseUrl = blankDefault(apiBaseUrl, "https://api.mercadopago.com");
			authorizationBaseUrl = blankDefault(authorizationBaseUrl, "https://auth.mercadopago.com.mx/authorization");
		}
	}

	private static String blankDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
