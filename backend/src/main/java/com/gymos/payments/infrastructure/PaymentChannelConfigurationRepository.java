package com.gymos.payments.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentChannelConfigurationRepository {
    private final JdbcClient jdbc;

    public PaymentChannelConfigurationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Configuration> find(UUID organizationId, UUID branchId) {
        return jdbc.sql("""
                SELECT organization_id, branch_id, mercado_pago_point_enabled,
                       mercado_pago_access_token_ref, mercado_pago_terminal_id,
                       external_terminal_manual_enabled, stripe_kiosk_enabled,
                       stripe_secret_key_ref, stripe_webhook_secret_ref,
                       stripe_autopay_enabled, environment, updated_at
                FROM payment_channel_configuration
                WHERE organization_id=:organizationId AND branch_id=:branchId
                """)
                .param("organizationId", organizationId).param("branchId", branchId)
                .query((rs, n) -> new Configuration(
                        rs.getObject("organization_id", UUID.class), rs.getObject("branch_id", UUID.class),
                        rs.getBoolean("mercado_pago_point_enabled"), rs.getString("mercado_pago_access_token_ref"),
                        rs.getString("mercado_pago_terminal_id"), rs.getBoolean("external_terminal_manual_enabled"),
                        rs.getBoolean("stripe_kiosk_enabled"), rs.getString("stripe_secret_key_ref"),
                        rs.getString("stripe_webhook_secret_ref"), rs.getBoolean("stripe_autopay_enabled"),
                        rs.getString("environment"), rs.getObject("updated_at", OffsetDateTime.class)
                )).optional();
    }

    public Configuration save(UUID organizationId, UUID branchId, ConfigurationInput input, UUID actorId) {
        jdbc.sql("""
                INSERT INTO payment_channel_configuration (
                    id, organization_id, branch_id, mercado_pago_point_enabled,
                    mercado_pago_access_token_ref, mercado_pago_terminal_id,
                    external_terminal_manual_enabled, stripe_kiosk_enabled,
                    stripe_secret_key_ref, stripe_webhook_secret_ref,
                    stripe_autopay_enabled, environment, updated_by, created_at, updated_at
                ) VALUES (
                    gen_random_uuid(), :organizationId, :branchId, :pointEnabled,
                    :pointTokenRef, :pointTerminalId, :manualEnabled, :stripeEnabled,
                    :stripeKeyRef, :stripeWebhookRef, :autopayEnabled, :environment,
                    :actorId, now(), now()
                )
                ON CONFLICT (organization_id, branch_id) DO UPDATE SET
                    mercado_pago_point_enabled=EXCLUDED.mercado_pago_point_enabled,
                    mercado_pago_access_token_ref=EXCLUDED.mercado_pago_access_token_ref,
                    mercado_pago_terminal_id=EXCLUDED.mercado_pago_terminal_id,
                    external_terminal_manual_enabled=EXCLUDED.external_terminal_manual_enabled,
                    stripe_kiosk_enabled=EXCLUDED.stripe_kiosk_enabled,
                    stripe_secret_key_ref=EXCLUDED.stripe_secret_key_ref,
                    stripe_webhook_secret_ref=EXCLUDED.stripe_webhook_secret_ref,
                    stripe_autopay_enabled=EXCLUDED.stripe_autopay_enabled,
                    environment=EXCLUDED.environment, updated_by=EXCLUDED.updated_by,
                    updated_at=now(), version=payment_channel_configuration.version+1
                """)
                .param("organizationId", organizationId).param("branchId", branchId)
                .param("pointEnabled", input.mercadoPagoPointEnabled())
                .param("pointTokenRef", input.mercadoPagoAccessTokenRef())
                .param("pointTerminalId", input.mercadoPagoTerminalId())
                .param("manualEnabled", input.externalTerminalManualEnabled())
                .param("stripeEnabled", input.stripeKioskEnabled())
                .param("stripeKeyRef", input.stripeSecretKeyRef())
                .param("stripeWebhookRef", input.stripeWebhookSecretRef())
                .param("autopayEnabled", input.stripeAutopayEnabled())
                .param("environment", input.environment()).param("actorId", actorId).update();
        return find(organizationId, branchId).orElseThrow();
    }

    public record Configuration(UUID organizationId, UUID branchId, boolean mercadoPagoPointEnabled,
            String mercadoPagoAccessTokenRef, String mercadoPagoTerminalId,
            boolean externalTerminalManualEnabled, boolean stripeKioskEnabled,
            String stripeSecretKeyRef, String stripeWebhookSecretRef,
            boolean stripeAutopayEnabled, String environment, OffsetDateTime updatedAt) {}

    public record ConfigurationInput(boolean mercadoPagoPointEnabled, String mercadoPagoAccessTokenRef,
            String mercadoPagoTerminalId, boolean externalTerminalManualEnabled,
            boolean stripeKioskEnabled, String stripeSecretKeyRef,
            String stripeWebhookSecretRef, boolean stripeAutopayEnabled, String environment) {}
}
