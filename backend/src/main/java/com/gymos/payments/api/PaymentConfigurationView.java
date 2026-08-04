package com.gymos.payments.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentConfigurationView(
		boolean receptionManualTerminalEnabled,
		boolean receptionMercadoPagoEnabled,
		String mercadoPagoAccountId,
		String mercadoPagoTerminalId,
		boolean mercadoPagoConnected,
		boolean stripeSelfCheckoutEnabled,
		boolean stripeRecurringEnabled,
		String stripeAccountId,
		boolean stripeConnected
) {
	@JsonProperty
	public boolean mercadoPagoReady() {
		return receptionMercadoPagoEnabled && mercadoPagoConnected
				&& mercadoPagoTerminalId != null && !mercadoPagoTerminalId.isBlank();
	}

	@JsonProperty
	public boolean stripeCheckoutReady() {
		return stripeSelfCheckoutEnabled && stripeConnected;
	}

	@JsonProperty
	public boolean stripeRecurringReady() {
		return stripeRecurringEnabled && stripeCheckoutReady();
	}
}
