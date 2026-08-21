package com.gymos.payments.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentConfigurationService implements PaymentConfigurationOperations {
	private final PaymentConfigurationRepository repository;
	private final AuditService auditService;

	public PaymentConfigurationService(PaymentConfigurationRepository repository, AuditService auditService) {
		this.repository = repository;
		this.auditService = auditService;
	}

	@Transactional
	public PaymentConfigurationView configuration(GymOsPrincipal principal) {
		requireTenant(principal);
		return repository.configuration(principal.organizationId());
	}

	@Transactional
	@Override
	public PaymentConfigurationView configuration(java.util.UUID organizationId) {
		return repository.configuration(organizationId);
	}

	@Transactional
	public PaymentConfigurationView update(
			GymOsPrincipal principal,
			UpdatePaymentConfigurationCommand command,
			String correlationId
	) {
		requireTenant(principal);
		PaymentConfigurationView current = repository.configuration(principal.organizationId());
		if (command.receptionManualTerminalEnabled() && command.receptionMercadoPagoEnabled()) {
			throw new BusinessException(
					"RECEPTION_CARD_METHOD_EXCLUSIVE",
					"Selecciona una sola forma de cobro con tarjeta en recepcion: terminal independiente o Mercado Pago Point."
			);
		}
		if (command.receptionMercadoPagoEnabled()
				&& (!current.mercadoPagoConnected() || blank(command.mercadoPagoTerminalId()))) {
			throw new BusinessException(
					"MERCADO_PAGO_NOT_CONNECTED",
					"Vincula la cuenta de Mercado Pago y selecciona una terminal Point antes de habilitar cobro integrado."
			);
		}
		if (command.stripeSelfCheckoutEnabled() && !current.stripeConnected()) {
			throw new BusinessException(
					"STRIPE_NOT_CONNECTED",
					"Vincula la cuenta de Stripe antes de habilitar self checkout."
			);
		}
		if (command.stripeRecurringEnabled() && !command.stripeSelfCheckoutEnabled()) {
			throw new BusinessException(
					"STRIPE_CHECKOUT_REQUIRED",
					"El cobro recurrente requiere que Stripe self checkout este habilitado."
			);
		}
		repository.update(principal.organizationId(), principal.userId(), command);
		auditService.record(
				principal,
				"PAYMENT_CONFIGURATION_UPDATED",
				"ORGANIZATION_PAYMENT_CONFIGURATION",
				principal.organizationId(),
				"BACKOFFICE",
				null,
				correlationId
		);
		return repository.configuration(principal.organizationId());
	}

	@Override
	public void requireReceptionMethod(java.util.UUID organizationId, String method) {
		PaymentConfigurationView configuration = repository.configuration(organizationId);
		if ("CARD_MANUAL".equalsIgnoreCase(method) && !configuration.receptionManualTerminalEnabled()) {
			throw new BusinessException(
					"RECEPTION_MANUAL_CARD_DISABLED",
					"El dueno del gimnasio deshabilito el registro manual de pagos con terminal."
			);
		}
		if ("CARD_MERCADO_PAGO".equalsIgnoreCase(method) && !configuration.mercadoPagoReady()) {
			throw new BusinessException(
					"RECEPTION_MERCADO_PAGO_DISABLED",
					"Mercado Pago Point no esta vinculado, habilitado o no tiene una terminal seleccionada."
			);
		}
	}

	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("ORGANIZATION_REQUIRED", "Selecciona un gimnasio para continuar.");
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
