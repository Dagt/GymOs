package com.gymos.automations.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CommunicationTemplateResolver {
    private final JdbcClient jdbcClient;

    public CommunicationTemplateResolver(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ResolvedTemplate> active(
            UUID organizationId,
            UUID branchId,
            String code,
            String channel,
            String locale
    ) {
        return jdbcClient.sql("""
                SELECT code, channel, locale, subject, body, allowed_variables_json, version
                FROM communication_template
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND code = :code
                  AND channel = :channel
                  AND locale = :locale
                  AND status = 'ACTIVE'
                ORDER BY CASE WHEN branch_id = :branchId THEN 0 ELSE 1 END, version DESC
                LIMIT 1
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("code", code)
                .param("channel", channel)
                .param("locale", locale)
                .query(ResolvedTemplate.class)
                .optional();
    }

    public record ResolvedTemplate(
            String code,
            String channel,
            String locale,
            String subject,
            String body,
            String allowedVariablesJson,
            int version
    ) {}
}
