package com.gymos.selfservice.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SelfServiceCheckoutRepository {
    private final JdbcClient jdbcClient;

    public SelfServiceCheckoutRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public QuoteRecord createQuote(CreateQuoteData data) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("""
                INSERT INTO self_service_quote (
                    id, organization_id, branch_id, session_id, client_id,
                    membership_id, operation_type, plan_id, plan_name,
                    list_price_cents, discount_cents, total_cents, currency,
                    applied_benefits_json, status, expires_at, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :branchId, :sessionId, :clientId,
                    :membershipId, :operationType, :planId, :planName,
                    :listPriceCents, :discountCents, :totalCents, :currency,
                    CAST(:benefitsJson AS jsonb), 'ACTIVE', :expiresAt, :createdAt, :updatedAt
                )
                """)
                .param("id", id)
                .param("organizationId", data.organizationId())
                .param("branchId", data.branchId())
                .param("sessionId", data.sessionId())
                .param("clientId", data.clientId())
                .param("membershipId", data.membershipId())
                .param("operationType", data.operationType())
                .param("planId", data.planId())
                .param("planName", data.planName())
                .param("listPriceCents", data.listPriceCents())
                .param("discountCents", data.discountCents())
                .param("totalCents", data.totalCents())
                .param("currency", data.currency())
                .param("benefitsJson", data.benefitsJson())
                .param("expiresAt", data.expiresAt())
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();
        return quote(id).orElseThrow();
    }

    public Optional<QuoteRecord> activeQuote(UUID quoteId, UUID sessionId, OffsetDateTime now) {
        return jdbcClient.sql(selectQuoteSql() + """
                WHERE id = :quoteId
                  AND session_id = :sessionId
                  AND status = 'ACTIVE'
                  AND expires_at > :now
                """)
                .param("quoteId", quoteId)
                .param("sessionId", sessionId)
                .param("now", now)
                .query(this::mapQuote)
                .optional();
    }

    public Optional<QuoteRecord> quote(UUID id) {
        return jdbcClient.sql(selectQuoteSql() + " WHERE id = :id")
                .param("id", id)
                .query(this::mapQuote)
                .optional();
    }

    public boolean consumeQuote(UUID quoteId, OffsetDateTime now) {
        return jdbcClient.sql("""
                UPDATE self_service_quote
                SET status = 'CONSUMED', consumed_at = :now, updated_at = :now
                WHERE id = :quoteId
                  AND status = 'ACTIVE'
                  AND expires_at > :now
                """)
                .param("quoteId", quoteId)
                .param("now", now)
                .update() == 1;
    }

    public PaymentIntentRecord createPaymentIntent(CreatePaymentIntentData data) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbcClient.sql("""
                    INSERT INTO self_service_payment_intent (
                        id, organization_id, branch_id, session_id, client_id, quote_id,
                        provider, provider_intent_id, idempotency_key, amount_cents,
                        currency, status, checkout_url, client_secret, expires_at,
                        created_at, updated_at
                    ) VALUES (
                        :id, :organizationId, :branchId, :sessionId, :clientId, :quoteId,
                        :provider, :providerIntentId, :idempotencyKey, :amountCents,
                        :currency, :status, :checkoutUrl, :clientSecret, :expiresAt,
                        :createdAt, :updatedAt
                    )
                    """)
                    .param("id", id)
                    .param("organizationId", data.organizationId())
                    .param("branchId", data.branchId())
                    .param("sessionId", data.sessionId())
                    .param("clientId", data.clientId())
                    .param("quoteId", data.quoteId())
                    .param("provider", data.provider())
                    .param("providerIntentId", data.providerIntentId())
                    .param("idempotencyKey", data.idempotencyKey())
                    .param("amountCents", data.amountCents())
                    .param("currency", data.currency())
                    .param("status", data.status())
                    .param("checkoutUrl", data.checkoutUrl())
                    .param("clientSecret", data.clientSecret())
                    .param("expiresAt", data.expiresAt())
                    .param("createdAt", now)
                    .param("updatedAt", now)
                    .update();
        } catch (DuplicateKeyException ignored) {
            return paymentIntentByIdempotency(data.organizationId(), data.idempotencyKey()).orElseThrow();
        }
        return paymentIntent(id).orElseThrow();
    }

    public Optional<PaymentIntentRecord> paymentIntent(UUID id) {
        return jdbcClient.sql(selectIntentSql() + " WHERE id = :id")
                .param("id", id)
                .query(this::mapIntent)
                .optional();
    }

    public Optional<PaymentIntentRecord> paymentIntentByIdempotency(UUID organizationId, String key) {
        return jdbcClient.sql(selectIntentSql() + """
                WHERE organization_id = :organizationId
                  AND idempotency_key = :key
                """)
                .param("organizationId", organizationId)
                .param("key", key)
                .query(this::mapIntent)
                .optional();
    }

    public Optional<PaymentIntentRecord> paymentIntentByProvider(String provider, String providerIntentId) {
        return jdbcClient.sql(selectIntentSql() + """
                WHERE provider = :provider
                  AND provider_intent_id = :providerIntentId
                """)
                .param("provider", provider)
                .param("providerIntentId", providerIntentId)
                .query(this::mapIntent)
                .optional();
    }

    public boolean updatePaymentStatus(
            UUID id,
            String expectedCurrentStatus,
            String newStatus,
            String error,
            OffsetDateTime completedAt
    ) {
        return jdbcClient.sql("""
                UPDATE self_service_payment_intent
                SET status = :newStatus,
                    last_error = :error,
                    completed_at = :completedAt,
                    updated_at = :now
                WHERE id = :id
                  AND status = :expectedCurrentStatus
                """)
                .param("id", id)
                .param("expectedCurrentStatus", expectedCurrentStatus)
                .param("newStatus", newStatus)
                .param("error", truncate(error))
                .param("completedAt", completedAt)
                .param("now", OffsetDateTime.now())
                .update() == 1;
    }

    private String selectQuoteSql() {
        return """
                SELECT id, organization_id, branch_id, session_id, client_id,
                       membership_id, operation_type, plan_id, plan_name,
                       list_price_cents, discount_cents, total_cents, currency,
                       applied_benefits_json::text AS applied_benefits_json,
                       status, expires_at, consumed_at, created_at, updated_at
                FROM self_service_quote
                """;
    }

    private String selectIntentSql() {
        return """
                SELECT id, organization_id, branch_id, session_id, client_id, quote_id,
                       provider, provider_intent_id, idempotency_key, amount_cents,
                       currency, status, checkout_url, client_secret, expires_at,
                       completed_at, last_error, created_at, updated_at
                FROM self_service_payment_intent
                """;
    }

    private QuoteRecord mapQuote(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        return new QuoteRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("client_id", UUID.class),
                rs.getObject("membership_id", UUID.class),
                rs.getString("operation_type"),
                rs.getObject("plan_id", UUID.class),
                rs.getString("plan_name"),
                rs.getLong("list_price_cents"),
                rs.getLong("discount_cents"),
                rs.getLong("total_cents"),
                rs.getString("currency"),
                rs.getString("applied_benefits_json"),
                rs.getString("status"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("consumed_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private PaymentIntentRecord mapIntent(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        return new PaymentIntentRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("client_id", UUID.class),
                rs.getObject("quote_id", UUID.class),
                rs.getString("provider"),
                rs.getString("provider_intent_id"),
                rs.getString("idempotency_key"),
                rs.getLong("amount_cents"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("checkout_url"),
                rs.getString("client_secret"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("last_error"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public record CreateQuoteData(
            UUID organizationId,
            UUID branchId,
            UUID sessionId,
            UUID clientId,
            UUID membershipId,
            String operationType,
            UUID planId,
            String planName,
            long listPriceCents,
            long discountCents,
            long totalCents,
            String currency,
            String benefitsJson,
            OffsetDateTime expiresAt
    ) {}

    public record QuoteRecord(
            UUID id,
            UUID organizationId,
            UUID branchId,
            UUID sessionId,
            UUID clientId,
            UUID membershipId,
            String operationType,
            UUID planId,
            String planName,
            long listPriceCents,
            long discountCents,
            long totalCents,
            String currency,
            String benefitsJson,
            String status,
            OffsetDateTime expiresAt,
            OffsetDateTime consumedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CreatePaymentIntentData(
            UUID organizationId,
            UUID branchId,
            UUID sessionId,
            UUID clientId,
            UUID quoteId,
            String provider,
            String providerIntentId,
            String idempotencyKey,
            long amountCents,
            String currency,
            String status,
            String checkoutUrl,
            String clientSecret,
            OffsetDateTime expiresAt
    ) {}

    public record PaymentIntentRecord(
            UUID id,
            UUID organizationId,
            UUID branchId,
            UUID sessionId,
            UUID clientId,
            UUID quoteId,
            String provider,
            String providerIntentId,
            String idempotencyKey,
            long amountCents,
            String currency,
            String status,
            String checkoutUrl,
            String clientSecret,
            OffsetDateTime expiresAt,
            OffsetDateTime completedAt,
            String lastError,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
