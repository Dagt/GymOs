package com.gymos.payments.application;

public record UpdatePaymentConfigurationCommand(
		boolean receptionManualTerminalEnabled,
		boolean receptionMercadoPagoEnabled,
		String mercadoPagoTerminalId,
		boolean stripeSelfCheckoutEnabled,
		boolean stripeRecurringEnabled
) {
}
