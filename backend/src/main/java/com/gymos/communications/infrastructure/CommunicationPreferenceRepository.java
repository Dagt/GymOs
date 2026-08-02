package com.gymos.communications.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CommunicationPreferenceRepository {
    private final JdbcClient jdbcClient;

    public CommunicationPreferenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public CommunicationPreference resolve(UUID organizationId, UUID clientId) {
        return jdbcClient.sql("""
                SELECT email_operational_enabled, whatsapp_operational_enabled,
                       email_marketing_enabled, whatsapp_marketing_enabled,
                       consent_version, consented_at, revoked_at
                FROM client_communication_preference
                WHERE organization_id = :organizationId AND client_id = :clientId
                """)
                .param("organizationId", organizationId)
                .param("clientId", clientId)
                .query((rs, rowNum) -> new CommunicationPreference(
                        rs.getBoolean("email_operational_enabled"),
                        rs.getBoolean("whatsapp_operational_enabled"),
                        rs.getBoolean("email_marketing_enabled"),
                        rs.getBoolean("whatsapp_marketing_enabled"),
                        rs.getString("consent_version"),
                        rs.getObject("consented_at", java.time.OffsetDateTime.class),
                        rs.getObject("revoked_at", java.time.OffsetDateTime.class)
                ))
                .optional()
                .orElseGet(CommunicationPreference::operationalDefaults);
    }

    public record CommunicationPreference(
            boolean emailOperationalEnabled,
            boolean whatsappOperationalEnabled,
            boolean emailMarketingEnabled,
            boolean whatsappMarketingEnabled,
            String consentVersion,
            java.time.OffsetDateTime consentedAt,
            java.time.OffsetDateTime revokedAt
    ) {
        public static CommunicationPreference operationalDefaults() {
            return new CommunicationPreference(true, true, false, false, null, null, null);
        }

        public boolean allows(String channel, String communicationType) {
            boolean marketing = "MARKETING".equalsIgnoreCase(communicationType);
            return switch (channel.toUpperCase(java.util.Locale.ROOT)) {
                case "EMAIL" -> marketing ? emailMarketingEnabled : emailOperationalEnabled;
                case "WHATSAPP" -> marketing ? whatsappMarketingEnabled : whatsappOperationalEnabled;
                default -> false;
            };
        }
    }
}
