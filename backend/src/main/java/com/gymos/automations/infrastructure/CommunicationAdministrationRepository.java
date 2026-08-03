package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CommunicationAdministrationRepository {
    private final JdbcClient jdbcClient;

    public CommunicationAdministrationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TemplateRecord> templates(UUID organizationId, UUID branchId) {
        return jdbcClient.sql("""
                SELECT id, organization_id, branch_id, code, channel, locale, subject, body,
                       allowed_variables_json, version, status, created_at, updated_at
                FROM communication_template
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                ORDER BY code, channel, locale, version DESC
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .query(TemplateRecord.class)
                .list();
    }

    public Optional<TemplateRecord> template(UUID organizationId, UUID branchId, UUID id) {
        return jdbcClient.sql("""
                SELECT id, organization_id, branch_id, code, channel, locale, subject, body,
                       allowed_variables_json, version, status, created_at, updated_at
                FROM communication_template
                WHERE id = :id AND organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .query(TemplateRecord.class)
                .optional();
    }

    public UUID createTemplate(UUID organizationId, UUID branchId, UUID actorUserId, TemplateDraft draft) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int nextVersion = jdbcClient.sql("""
                SELECT COALESCE(MAX(version), 0) + 1
                FROM communication_template
                WHERE organization_id = :organizationId
                  AND branch_id IS NOT DISTINCT FROM :branchId
                  AND code = :code AND channel = :channel AND locale = :locale
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("code", draft.code())
                .param("channel", draft.channel())
                .param("locale", draft.locale())
                .query(Integer.class)
                .single();
        jdbcClient.sql("""
                INSERT INTO communication_template (
                    id, organization_id, branch_id, code, channel, locale, subject, body,
                    allowed_variables_json, version, status, created_by, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :branchId, :code, :channel, :locale, :subject, :body,
                    :variables, :version, :status, :createdBy, :now, :now
                )
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("code", draft.code())
                .param("channel", draft.channel())
                .param("locale", draft.locale())
                .param("subject", draft.subject())
                .param("body", draft.body())
                .param("variables", draft.allowedVariablesJson())
                .param("version", nextVersion)
                .param("status", draft.status())
                .param("createdBy", actorUserId)
                .param("now", now)
                .update();
        return id;
    }

    public boolean archiveTemplate(UUID organizationId, UUID branchId, UUID id) {
        return jdbcClient.sql("""
                UPDATE communication_template
                SET status = 'ARCHIVED', updated_at = now()
                WHERE id = :id AND organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND status <> 'ARCHIVED'
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .update() == 1;
    }

    public PreferenceRecord preference(UUID organizationId, UUID clientId) {
        return jdbcClient.sql("""
                SELECT organization_id, client_id, email_operational_enabled,
                       whatsapp_operational_enabled, email_marketing_enabled,
                       whatsapp_marketing_enabled, consent_version, consented_at,
                       revoked_at, created_at, updated_at
                FROM client_communication_preference
                WHERE organization_id = :organizationId AND client_id = :clientId
                """)
                .param("organizationId", organizationId)
                .param("clientId", clientId)
                .query(PreferenceRecord.class)
                .optional()
                .orElse(new PreferenceRecord(
                        organizationId, clientId, true, true, false, false,
                        null, null, null, null, null
                ));
    }

    public PreferenceRecord savePreference(UUID organizationId, UUID clientId, UUID actorUserId, PreferenceUpdate update) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("""
                INSERT INTO client_communication_preference (
                    organization_id, client_id, email_operational_enabled,
                    whatsapp_operational_enabled, email_marketing_enabled,
                    whatsapp_marketing_enabled, consent_version, consented_at,
                    revoked_at, updated_by, created_at, updated_at
                ) VALUES (
                    :organizationId, :clientId, :emailOperational, :whatsappOperational,
                    :emailMarketing, :whatsappMarketing, :consentVersion, :consentedAt,
                    :revokedAt, :updatedBy, :now, :now
                )
                ON CONFLICT (organization_id, client_id) DO UPDATE SET
                    email_operational_enabled = EXCLUDED.email_operational_enabled,
                    whatsapp_operational_enabled = EXCLUDED.whatsapp_operational_enabled,
                    email_marketing_enabled = EXCLUDED.email_marketing_enabled,
                    whatsapp_marketing_enabled = EXCLUDED.whatsapp_marketing_enabled,
                    consent_version = EXCLUDED.consent_version,
                    consented_at = EXCLUDED.consented_at,
                    revoked_at = EXCLUDED.revoked_at,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """)
                .param("organizationId", organizationId)
                .param("clientId", clientId)
                .param("emailOperational", update.emailOperationalEnabled())
                .param("whatsappOperational", update.whatsappOperationalEnabled())
                .param("emailMarketing", update.emailMarketingEnabled())
                .param("whatsappMarketing", update.whatsappMarketingEnabled())
                .param("consentVersion", update.consentVersion())
                .param("consentedAt", update.consented() ? now : null)
                .param("revokedAt", update.consented() ? null : now)
                .param("updatedBy", actorUserId)
                .param("now", now)
                .update();
        return preference(organizationId, clientId);
    }

    public List<DeliverySummary> deliverySummary(UUID organizationId, UUID branchId, OffsetDateTime from) {
        return jdbcClient.sql("""
                SELECT channel, status, COUNT(*) AS total
                FROM outbound_message
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR :branchId IS NULL)
                  AND created_at >= :from
                GROUP BY channel, status
                ORDER BY channel, status
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("from", from)
                .query(DeliverySummary.class)
                .list();
    }

    public record TemplateDraft(String code, String channel, String locale, String subject,
                                String body, String allowedVariablesJson, String status) {}

    public record TemplateRecord(UUID id, UUID organizationId, UUID branchId, String code,
                                 String channel, String locale, String subject, String body,
                                 String allowedVariablesJson, int version, String status,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record PreferenceUpdate(boolean emailOperationalEnabled, boolean whatsappOperationalEnabled,
                                   boolean emailMarketingEnabled, boolean whatsappMarketingEnabled,
                                   String consentVersion, boolean consented) {}

    public record PreferenceRecord(UUID organizationId, UUID clientId,
                                   boolean emailOperationalEnabled, boolean whatsappOperationalEnabled,
                                   boolean emailMarketingEnabled, boolean whatsappMarketingEnabled,
                                   String consentVersion, OffsetDateTime consentedAt, OffsetDateTime revokedAt,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record DeliverySummary(String channel, String status, long total) {}
}
