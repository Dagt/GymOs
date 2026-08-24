package com.gymos.platformbilling.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformBillingRepository {
    private static final UUID PLATFORM_ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final JdbcClient jdbcClient;

    public PlatformBillingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<BranchBillingRecord> findAll() {
        return jdbcClient.sql(baseSelect() + "\nORDER BY LOWER(o.trade_name), LOWER(b.name)")
                .param("platformOrganizationId", PLATFORM_ORGANIZATION_ID)
                .query(this::mapRecord)
                .list();
    }

    public Optional<BranchBillingRecord> findByBranchId(UUID branchId) {
        return jdbcClient.sql(baseSelect() + "\nAND b.id = :branchId")
                .param("platformOrganizationId", PLATFORM_ORGANIZATION_ID)
                .param("branchId", branchId)
                .query(this::mapRecord)
                .optional();
    }

    public int upsert(BillingUpdateRecord record) {
        return jdbcClient.sql("""
                INSERT INTO platform_branch_billing (
                    branch_id,
                    organization_id,
                    subscription_status,
                    billing_anchor_day,
                    next_billing_date,
                    plan_override,
                    monthly_price_override,
                    implementation_fee,
                    implementation_paid,
                    notes,
                    version,
                    created_at,
                    updated_at,
                    updated_by
                )
                SELECT
                    b.id,
                    b.organization_id,
                    :subscriptionStatus,
                    :billingAnchorDay,
                    :nextBillingDate,
                    :planOverride,
                    :monthlyPriceOverride,
                    :implementationFee,
                    :implementationPaid,
                    :notes,
                    1,
                    :now,
                    :now,
                    :updatedBy
                FROM branch b
                WHERE b.id = :branchId
                  AND b.deleted_at IS NULL
                ON CONFLICT (branch_id) DO UPDATE SET
                    subscription_status = EXCLUDED.subscription_status,
                    billing_anchor_day = EXCLUDED.billing_anchor_day,
                    next_billing_date = EXCLUDED.next_billing_date,
                    plan_override = EXCLUDED.plan_override,
                    monthly_price_override = EXCLUDED.monthly_price_override,
                    implementation_fee = EXCLUDED.implementation_fee,
                    implementation_paid = EXCLUDED.implementation_paid,
                    notes = EXCLUDED.notes,
                    version = platform_branch_billing.version + 1,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by
                WHERE platform_branch_billing.version = :expectedVersion
                """)
                .param("branchId", record.branchId())
                .param("subscriptionStatus", record.subscriptionStatus())
                .param("billingAnchorDay", record.billingAnchorDay())
                .param("nextBillingDate", record.nextBillingDate())
                .param("planOverride", record.planOverride())
                .param("monthlyPriceOverride", record.monthlyPriceOverride())
                .param("implementationFee", record.implementationFee())
                .param("implementationPaid", record.implementationPaid())
                .param("notes", record.notes())
                .param("expectedVersion", record.expectedVersion())
                .param("now", record.now())
                .param("updatedBy", record.updatedBy())
                .update();
    }

    private String baseSelect() {
        return """
                SELECT
                    o.id AS organization_id,
                    o.trade_name AS organization_name,
                    o.status AS organization_status,
                    b.id AS branch_id,
                    b.name AS branch_name,
                    b.code AS branch_code,
                    b.status AS branch_status,
                    (
                        SELECT COUNT(*)
                        FROM user_account u
                        WHERE u.organization_id = o.id
                          AND u.status = 'ACTIVE'
                    ) AS active_team_user_count,
                    (
                        SELECT COUNT(*)
                        FROM membership_plan mp
                        WHERE mp.organization_id = o.id
                          AND mp.status = 'ACTIVE'
                          AND mp.deleted_at IS NULL
                    ) AS active_membership_plan_count,
                    (
                        SELECT COUNT(DISTINCT m.client_id)
                        FROM membership m
                        WHERE m.organization_id = o.id
                          AND m.branch_id = b.id
                          AND m.status = 'ACTIVE'
                          AND m.deleted_at IS NULL
                          AND m.starts_on <= CURRENT_DATE
                          AND m.grace_ends_on >= CURRENT_DATE
                    ) AS active_members,
                    pbb.subscription_status,
                    pbb.billing_anchor_day,
                    pbb.next_billing_date,
                    pbb.last_payment_date,
                    pbb.last_payment_amount,
                    pbb.plan_override,
                    pbb.monthly_price_override,
                    pbb.implementation_fee,
                    pbb.implementation_paid,
                    pbb.notes,
                    COALESCE(pbb.version, 0) AS version
                FROM organization o
                JOIN branch b
                  ON b.organization_id = o.id
                 AND b.deleted_at IS NULL
                LEFT JOIN platform_branch_billing pbb
                  ON pbb.branch_id = b.id
                WHERE o.deleted_at IS NULL
                  AND o.id <> :platformOrganizationId
                """;
    }

    private BranchBillingRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        Number billingAnchorValue = (Number) resultSet.getObject("billing_anchor_day");
        Integer billingAnchorDay = billingAnchorValue == null ? null : billingAnchorValue.intValue();
        BigDecimal implementationFee = resultSet.getBigDecimal("implementation_fee");
        return new BranchBillingRecord(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("organization_name"),
                resultSet.getString("organization_status"),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getString("branch_name"),
                resultSet.getString("branch_code"),
                resultSet.getString("branch_status"),
                resultSet.getInt("active_team_user_count"),
                resultSet.getInt("active_membership_plan_count"),
                resultSet.getInt("active_members"),
                resultSet.getString("subscription_status"),
                billingAnchorDay,
                resultSet.getObject("next_billing_date", LocalDate.class),
                resultSet.getObject("last_payment_date", LocalDate.class),
                resultSet.getBigDecimal("last_payment_amount"),
                resultSet.getString("plan_override"),
                resultSet.getBigDecimal("monthly_price_override"),
                implementationFee == null ? BigDecimal.ZERO : implementationFee,
                resultSet.getBoolean("implementation_paid"),
                resultSet.getString("notes"),
                resultSet.getLong("version")
        );
    }

    public List<BillingPaymentRecord> findPayments(UUID branchId) {
        return jdbcClient.sql("""
                SELECT id, branch_id, organization_id, payment_type, payment_method, amount,
                       paid_on, reference, notes, idempotency_key, recorded_by, created_at
                FROM platform_billing_payment
                WHERE branch_id = :branchId
                ORDER BY paid_on DESC, created_at DESC
                LIMIT 50
                """)
                .param("branchId", branchId)
                .query(this::mapPayment)
                .list();
    }

    public int insertPayment(BillingPaymentWrite record) {
        return jdbcClient.sql("""
                INSERT INTO platform_billing_payment (
                    id, branch_id, organization_id, payment_type, payment_method, amount,
                    paid_on, reference, notes, idempotency_key, recorded_by, created_at
                ) VALUES (
                    :id, :branchId, :organizationId, :paymentType, :paymentMethod, :amount,
                    :paidOn, :reference, :notes, :idempotencyKey, :recordedBy, :createdAt
                )
                ON CONFLICT (branch_id, idempotency_key) DO NOTHING
                """)
                .param("id", record.id())
                .param("branchId", record.branchId())
                .param("organizationId", record.organizationId())
                .param("paymentType", record.paymentType())
                .param("paymentMethod", record.paymentMethod())
                .param("amount", record.amount())
                .param("paidOn", record.paidOn())
                .param("reference", record.reference())
                .param("notes", record.notes())
                .param("idempotencyKey", record.idempotencyKey())
                .param("recordedBy", record.recordedBy())
                .param("createdAt", record.createdAt())
                .update();
    }

    public Optional<BillingPaymentRecord> findPaymentByIdempotencyKey(UUID branchId, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT id, branch_id, organization_id, payment_type, payment_method, amount,
                       paid_on, reference, notes, idempotency_key, recorded_by, created_at
                FROM platform_billing_payment
                WHERE branch_id = :branchId
                  AND idempotency_key = :idempotencyKey
                """)
                .param("branchId", branchId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapPayment)
                .optional();
    }

    public void registerSubscriptionPayment(UUID branchId, LocalDate paidOn, BigDecimal amount, LocalDate nextBillingDate, UUID updatedBy, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE platform_branch_billing
                SET last_payment_date = :paidOn,
                    last_payment_amount = :amount,
                    next_billing_date = :nextBillingDate,
                    version = version + 1,
                    updated_by = :updatedBy,
                    updated_at = :now
                WHERE branch_id = :branchId
                """)
                .param("branchId", branchId)
                .param("paidOn", paidOn)
                .param("amount", amount)
                .param("nextBillingDate", nextBillingDate)
                .param("updatedBy", updatedBy)
                .param("now", now)
                .update();
    }

    public BigDecimal paymentTotal(UUID branchId, String paymentType) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(amount), 0)
                FROM platform_billing_payment
                WHERE branch_id = :branchId
                  AND payment_type = :paymentType
                """)
                .param("branchId", branchId)
                .param("paymentType", paymentType)
                .query(BigDecimal.class)
                .single();
    }

    public void markImplementationPaid(UUID branchId, UUID updatedBy, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE platform_branch_billing
                SET implementation_paid = TRUE,
                    version = version + 1,
                    updated_by = :updatedBy,
                    updated_at = :now
                WHERE branch_id = :branchId
                """)
                .param("branchId", branchId)
                .param("updatedBy", updatedBy)
                .param("now", now)
                .update();
    }

    private BillingPaymentRecord mapPayment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BillingPaymentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("payment_type"),
                resultSet.getString("payment_method"),
                resultSet.getBigDecimal("amount"),
                resultSet.getObject("paid_on", LocalDate.class),
                resultSet.getString("reference"),
                resultSet.getString("notes"),
                resultSet.getString("idempotency_key"),
                resultSet.getObject("recorded_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }

    public record BranchBillingRecord(
            UUID organizationId,
            String organizationName,
            String organizationStatus,
            UUID branchId,
            String branchName,
            String branchCode,
            String branchStatus,
            int activeTeamUserCount,
            int activeMembershipPlanCount,
            int activeMembers,
            String subscriptionStatus,
            Integer billingAnchorDay,
            LocalDate nextBillingDate,
            LocalDate lastPaymentDate,
            BigDecimal lastPaymentAmount,
            String planOverride,
            BigDecimal monthlyPriceOverride,
            BigDecimal implementationFee,
            boolean implementationPaid,
            String notes,
            long version
    ) {
    }

    public record BillingPaymentRecord(
            UUID id,
            UUID branchId,
            UUID organizationId,
            String paymentType,
            String paymentMethod,
            BigDecimal amount,
            LocalDate paidOn,
            String reference,
            String notes,
            String idempotencyKey,
            UUID recordedBy,
            OffsetDateTime createdAt
    ) {
    }

    public record BillingPaymentWrite(
            UUID id,
            UUID branchId,
            UUID organizationId,
            String paymentType,
            String paymentMethod,
            BigDecimal amount,
            LocalDate paidOn,
            String reference,
            String notes,
            String idempotencyKey,
            UUID recordedBy,
            OffsetDateTime createdAt
    ) {
    }

    public record BillingUpdateRecord(
            UUID branchId,
            String subscriptionStatus,
            int billingAnchorDay,
            LocalDate nextBillingDate,
            String planOverride,
            BigDecimal monthlyPriceOverride,
            BigDecimal implementationFee,
            boolean implementationPaid,
            String notes,
            long expectedVersion,
            UUID updatedBy,
            OffsetDateTime now
    ) {
    }
}
