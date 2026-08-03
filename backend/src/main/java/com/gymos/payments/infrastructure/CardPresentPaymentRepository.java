package com.gymos.payments.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CardPresentPaymentRepository {
    private final JdbcClient jdbc;
    public CardPresentPaymentRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Payment create(UUID organizationId, UUID branchId, UUID saleId, String mode,
            long amountCents, String currency, String idempotencyKey, String externalOrderId,
            String terminalId, String status) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO card_present_payment (id, organization_id, branch_id, sale_id,
                        mode, status, amount_cents, currency, idempotency_key,
                        external_order_id, terminal_id, created_at, updated_at)
                    VALUES (:id,:organizationId,:branchId,:saleId,:mode,:status,:amount,:currency,
                        :key,:externalOrderId,:terminalId,now(),now())
                    """).param("id",id).param("organizationId",organizationId).param("branchId",branchId)
                    .param("saleId",saleId).param("mode",mode).param("status",status)
                    .param("amount",amountCents).param("currency",currency).param("key",idempotencyKey)
                    .param("externalOrderId",externalOrderId).param("terminalId",terminalId).update();
            return find(organizationId, branchId, id).orElseThrow();
        } catch (DuplicateKeyException ignored) {
            return jdbc.sql("SELECT id FROM card_present_payment WHERE organization_id=:organizationId AND idempotency_key=:key")
                    .param("organizationId",organizationId).param("key",idempotencyKey).query(UUID.class).single()
                    .transform(existing -> find(organizationId, branchId, existing).orElseThrow());
        }
    }

    public Optional<Payment> find(UUID organizationId, UUID branchId, UUID id) {
        return jdbc.sql("""
                SELECT id,sale_id,mode,status,amount_cents,currency,external_order_id,terminal_id,
                       manual_reference,authorization_code,confirmed_by,confirmed_at,last_error,created_at,updated_at
                FROM card_present_payment WHERE id=:id AND organization_id=:organizationId AND branch_id=:branchId
                """).param("id",id).param("organizationId",organizationId).param("branchId",branchId)
                .query((rs,n)->new Payment(rs.getObject("id",UUID.class),rs.getObject("sale_id",UUID.class),
                        rs.getString("mode"),rs.getString("status"),rs.getLong("amount_cents"),rs.getString("currency"),
                        rs.getString("external_order_id"),rs.getString("terminal_id"),rs.getString("manual_reference"),
                        rs.getString("authorization_code"),rs.getObject("confirmed_by",UUID.class),
                        rs.getObject("confirmed_at",OffsetDateTime.class),rs.getString("last_error"),
                        rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class))).optional();
    }

    public Payment confirmManual(UUID organizationId, UUID branchId, UUID id, UUID actor,
            String reference, String authorizationCode) {
        int updated=jdbc.sql("""
                UPDATE card_present_payment SET status='APPROVED',manual_reference=:reference,
                    authorization_code=:authorizationCode,confirmed_by=:actor,confirmed_at=now(),updated_at=now()
                WHERE id=:id AND organization_id=:organizationId AND branch_id=:branchId
                  AND mode='EXTERNAL_TERMINAL_MANUAL' AND status='PENDING'
                """).param("id",id).param("organizationId",organizationId).param("branchId",branchId)
                .param("actor",actor).param("reference",reference).param("authorizationCode",authorizationCode).update();
        if(updated!=1) throw new IllegalStateException("Payment is not pending manual confirmation");
        return find(organizationId,branchId,id).orElseThrow();
    }

    public void updateStatus(UUID organizationId, UUID branchId, UUID id, String status, String error) {
        jdbc.sql("UPDATE card_present_payment SET status=:status,last_error=:error,updated_at=now(),confirmed_at=CASE WHEN :status='APPROVED' THEN now() ELSE confirmed_at END WHERE id=:id AND organization_id=:organizationId AND branch_id=:branchId")
                .param("status",status).param("error",error).param("id",id)
                .param("organizationId",organizationId).param("branchId",branchId).update();
    }

    public record Payment(UUID id,UUID saleId,String mode,String status,long amountCents,String currency,
            String externalOrderId,String terminalId,String manualReference,String authorizationCode,
            UUID confirmedBy,OffsetDateTime confirmedAt,String lastError,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
}
