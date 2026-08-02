package com.gymos.selfservice.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SelfServiceAutopayRepository {
    private final JdbcClient jdbcClient;

    public SelfServiceAutopayRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public MandateRecord createPending(
            UUID organizationId,
            UUID branchId,
            UUID clientId,
            UUID membershipId,
            String consentVersion,
            OffsetDateTime consentedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO self_service_autopay_mandate (
                    id, organization_id, branch_id, client_id, membership_id,
                    status, consent_version, consented_at, created_at, updated_at, version
                ) VALUES (
                    :id, :organizationId, :branchId, :clientId, :membershipId,
                    'PENDING_METHOD', :consentVersion, :consentedAt, :now, :now, 0
                )
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("clientId", clientId)
                .param("membershipId", membershipId)
                .param("consentVersion", consentVersion)
                .param("consentedAt", consentedAt)
                .param("now", OffsetDateTime.now())
                .update();
        addEvent(id, "CONSENT_CAPTURED", null, "{}");
        return mandate(id).orElseThrow();
    }

    public Optional<MandateRecord> mandate(UUID id) {
        return jdbcClient.sql(selectColumns() + " WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public Optional<MandateRecord> mandate(UUID organizationId, UUID branchId, UUID clientId, UUID id) {
        return jdbcClient.sql(selectColumns() + """
                WHERE id = :id
                  AND organization_id = :organizationId
                  AND branch_id = :branchId
                  AND client_id = :clientId
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("clientId", clientId)
                .query(this::map)
                .optional();
    }

    public List<MandateRecord> mandates(UUID organizationId, UUID branchId, UUID clientId) {
        return jdbcClient.sql(selectColumns() + """
                WHERE organization_id = :organizationId
                  AND branch_id = :branchId
                  AND client_id = :clientId
                ORDER BY created_at DESC
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("clientId", clientId)
                .query(this::map)
                .list();
    }

    public boolean revoke(UUID id, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbcClient.sql("""
                UPDATE self_service_autopay_mandate
                SET status = 'REVOKED',
                    revoked_at = :now,
                    revocation_reason = :reason,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                  AND status IN ('PENDING_METHOD', 'ACTIVE', 'PAUSED', 'FAILED')
                """)
                .param("id", id)
                .param("reason", reason)
                .param("now", now)
                .update();
        if (updated == 1) addEvent(id, "REVOKED", null, "{}");
        return updated == 1;
    }

    public void addEvent(UUID mandateId, String eventType, String reference, String detailJson) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("""
                INSERT INTO self_service_autopay_event (
                    id, mandate_id, event_type, event_reference, detail_json, occurred_at, created_at
                ) VALUES (
                    :id, :mandateId, :eventType, :reference, CAST(:detailJson AS jsonb), :occurredAt, :createdAt
                )
                """)
                .param("id", UUID.randomUUID())
                .param("mandateId", mandateId)
                .param("eventType", eventType)
                .param("reference", reference)
                .param("detailJson", detailJson == null || detailJson.isBlank() ? "{}" : detailJson)
                .param("occurredAt", now)
                .param("createdAt", now)
                .update();
    }

    private String selectColumns() {
        return """
                SELECT id, organization_id, branch_id, client_id, membership_id,
                       provider, provider_customer_id, provider_payment_method_id,
                       status, consent_version, consented_at, activated_at, revoked_at,
                       revocation_reason, next_charge_at, last_charge_at, last_charge_status,
                       created_at, updated_at
                FROM self_service_autopay_mandate
                """;
    }

    private MandateRecord map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MandateRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("client_id", UUID.class),
                rs.getObject("membership_id", UUID.class),
                rs.getString("provider"),
                rs.getString("provider_customer_id"),
                rs.getString("provider_payment_method_id"),
                rs.getString("status"),
                rs.getString("consent_version"),
                rs.getObject("consented_at", OffsetDateTime.class),
                rs.getObject("activated_at", OffsetDateTime.class),
                rs.getObject("revoked_at", OffsetDateTime.class),
                rs.getString("revocation_reason"),
                rs.getObject("next_charge_at", OffsetDateTime.class),
                rs.getObject("last_charge_at", OffsetDateTime.class),
                rs.getString("last_charge_status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    public record MandateRecord(
            UUID id,
            UUID organizationId,
            UUID branchId,
            UUID clientId,
            UUID membershipId,
            String provider,
            String providerCustomerId,
            String providerPaymentMethodId,
            String status,
            String consentVersion,
            OffsetDateTime consentedAt,
            OffsetDateTime activatedAt,
            OffsetDateTime revokedAt,
            String revocationReason,
            OffsetDateTime nextChargeAt,
            OffsetDateTime lastChargeAt,
            String lastChargeStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
