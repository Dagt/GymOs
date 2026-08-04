package com.gymos.payments.infrastructure;

import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.application.UpdatePaymentConfigurationCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentConfigurationRepository {
	private final JdbcClient jdbcClient;

	public PaymentConfigurationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public PaymentConfigurationView configuration(UUID organizationId) {
		ensureExists(organizationId);
		return jdbcClient.sql("""
				SELECT reception_manual_terminal_enabled, reception_mercado_pago_enabled,
				       mercado_pago_account_id, mercado_pago_terminal_id, mercado_pago_connected_at,
				       stripe_self_checkout_enabled, stripe_recurring_enabled,
				       stripe_account_id, stripe_connected_at
				FROM organization_payment_configuration
				WHERE organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new PaymentConfigurationView(
						resultSet.getBoolean("reception_manual_terminal_enabled"),
						resultSet.getBoolean("reception_mercado_pago_enabled"),
						resultSet.getString("mercado_pago_account_id"),
						resultSet.getString("mercado_pago_terminal_id"),
						resultSet.getObject("mercado_pago_connected_at", OffsetDateTime.class) != null,
						resultSet.getBoolean("stripe_self_checkout_enabled"),
						resultSet.getBoolean("stripe_recurring_enabled"),
						resultSet.getString("stripe_account_id"),
						resultSet.getObject("stripe_connected_at", OffsetDateTime.class) != null
				))
				.single();
	}

	public void update(UUID organizationId, UUID userId, UpdatePaymentConfigurationCommand command) {
		ensureExists(organizationId);
		jdbcClient.sql("""
				UPDATE organization_payment_configuration
				SET reception_manual_terminal_enabled = :manualEnabled,
				    reception_mercado_pago_enabled = :mercadoPagoEnabled,
				    mercado_pago_terminal_id = :mercadoPagoTerminalId,
				    stripe_self_checkout_enabled = :stripeCheckoutEnabled,
				    stripe_recurring_enabled = :stripeRecurringEnabled,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				""")
				.param("manualEnabled", command.receptionManualTerminalEnabled())
				.param("mercadoPagoEnabled", command.receptionMercadoPagoEnabled())
				.param("mercadoPagoTerminalId", blankToNull(command.mercadoPagoTerminalId()))
				.param("stripeCheckoutEnabled", command.stripeSelfCheckoutEnabled())
				.param("stripeRecurringEnabled", command.stripeRecurringEnabled())
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", userId)
				.param("organizationId", organizationId)
				.update();
	}

	public void selectMercadoPagoTerminal(UUID organizationId, UUID userId, String terminalId) {
		ensureExists(organizationId);
		jdbcClient.sql("""
				UPDATE organization_payment_configuration
				SET mercado_pago_terminal_id = :terminalId,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				""")
				.param("terminalId", blankToNull(terminalId))
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", userId)
				.param("organizationId", organizationId)
				.update();
	}

	private void ensureExists(UUID organizationId) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO organization_payment_configuration (
				    organization_id, reception_manual_terminal_enabled,
				    reception_mercado_pago_enabled, stripe_self_checkout_enabled,
				    stripe_recurring_enabled, created_at, updated_at, version
				)
				VALUES (:organizationId, TRUE, FALSE, FALSE, FALSE, :now, :now, 0)
				ON CONFLICT (organization_id) DO NOTHING
				""")
				.param("organizationId", organizationId)
				.param("now", now)
				.update();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
