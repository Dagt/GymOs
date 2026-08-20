package com.gymos.payments.infrastructure;

import com.gymos.common.api.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecurringBillingRepository {
	private final JdbcClient jdbcClient;

	public RecurringBillingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public CustomerRecord reserveCustomer(UUID organizationId, UUID clientId) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID id = jdbcClient.sql("""
				INSERT INTO payment_customer (
				    id, organization_id, client_id, provider, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :clientId, 'STRIPE', :now, :now, 0)
				ON CONFLICT (organization_id, client_id, provider) DO UPDATE
				SET updated_at = payment_customer.updated_at
				RETURNING id
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("now", now)
				.query(UUID.class)
				.single();
		return customer(id).orElseThrow();
	}

	public Optional<CustomerRecord> customer(UUID id) {
		return jdbcClient.sql("""
				SELECT id, organization_id, client_id, provider, external_customer_id
				FROM payment_customer
				WHERE id = :id
				""")
				.param("id", id)
				.query((rs, row) -> new CustomerRecord(
						rs.getObject("id", UUID.class),
						rs.getObject("organization_id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getString("provider"),
						rs.getString("external_customer_id")
				))
				.optional();
	}

	public void attachExternalCustomer(UUID customerId, String externalCustomerId) {
		jdbcClient.sql("""
				UPDATE payment_customer
				SET external_customer_id = :externalCustomerId,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :customerId
				  AND (external_customer_id IS NULL OR external_customer_id = :externalCustomerId)
				""")
				.param("customerId", customerId)
				.param("externalCustomerId", externalCustomerId)
				.param("now", OffsetDateTime.now())
				.update();
	}

	public Optional<MandateRecord> mandateByKey(UUID organizationId, String idempotencyKey) {
		return mandateQuery("WHERE mandate.organization_id = :organizationId AND mandate.idempotency_key = :idempotencyKey")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(this::mapMandate)
				.optional();
	}

	public UUID createPendingMandate(CreateMandateData data) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'REPLACED', updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND membership_id = :membershipId
				  AND status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED')
				  AND idempotency_key <> :idempotencyKey
				""")
				.param("organizationId", data.organizationId())
				.param("membershipId", data.membershipId())
				.param("idempotencyKey", data.idempotencyKey())
				.param("now", now)
				.update();
		UUID id = UUID.randomUUID();
		return jdbcClient.sql("""
				INSERT INTO recurring_payment_mandate (
				    id, organization_id, branch_id, client_id, membership_id, plan_id,
				    payment_customer_id, provider, status, terms_version, consent_text,
				    amount_rule, consented_at, consent_origin, idempotency_key,
				    next_charge_on, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :membershipId, :planId,
				    :paymentCustomerId, 'STRIPE', 'PENDING_SETUP', :termsVersion, :consentText,
				    :amountRule, :consentedAt, :consentOrigin, :idempotencyKey,
				    :nextChargeOn, :now, :now, 0
				)
				ON CONFLICT (organization_id, idempotency_key) DO UPDATE
				SET updated_at = recurring_payment_mandate.updated_at
				RETURNING id
				""")
				.param("id", id)
				.param("organizationId", data.organizationId())
				.param("branchId", data.branchId())
				.param("clientId", data.clientId())
				.param("membershipId", data.membershipId())
				.param("planId", data.planId())
				.param("paymentCustomerId", data.paymentCustomerId())
				.param("termsVersion", data.termsVersion())
				.param("consentText", data.consentText())
				.param("amountRule", data.amountRule())
				.param("consentedAt", data.consentedAt())
				.param("consentOrigin", data.consentOrigin())
				.param("idempotencyKey", data.idempotencyKey())
				.param("nextChargeOn", data.nextChargeOn())
				.param("now", now)
				.query(UUID.class)
				.single();
	}

	public Optional<MandateRecord> mandate(UUID mandateId) {
		return mandateQuery("WHERE mandate.id = :mandateId")
				.param("mandateId", mandateId)
				.query(this::mapMandate)
				.optional();
	}

	public Optional<MandateRecord> currentMandate(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId
	) {
		return mandateQuery("""
				WHERE mandate.organization_id = :organizationId
				  AND mandate.branch_id = :branchId
				  AND mandate.client_id = :clientId
				  AND mandate.membership_id = :membershipId
				  AND mandate.status NOT IN ('REPLACED')
				ORDER BY CASE mandate.status WHEN 'PENDING_SETUP' THEN 0 ELSE 1 END,
				         mandate.created_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("membershipId", membershipId)
				.query(this::mapMandate)
				.optional();
	}

	public MandateRecord activateMandate(
			UUID mandateId,
			String externalPaymentMethodId,
			String methodType,
			String brand,
			String lastFour,
			Integer expiresMonth,
			Integer expiresYear
	) {
		MandateRecord mandate = mandate(mandateId).orElseThrow();
		OffsetDateTime now = OffsetDateTime.now();
		UUID methodId = jdbcClient.sql("""
				INSERT INTO stored_payment_method (
				    id, organization_id, client_id, payment_customer_id, provider,
				    external_payment_method_id, method_type, brand, last_four,
				    expires_month, expires_year, status, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :clientId, :paymentCustomerId, 'STRIPE',
				    :externalPaymentMethodId, :methodType, :brand, :lastFour,
				    :expiresMonth, :expiresYear, 'ACTIVE', :now, :now, 0
				)
				ON CONFLICT (organization_id, provider, external_payment_method_id) DO UPDATE
				SET method_type = EXCLUDED.method_type, brand = EXCLUDED.brand,
				    last_four = EXCLUDED.last_four, expires_month = EXCLUDED.expires_month,
				    expires_year = EXCLUDED.expires_year, status = 'ACTIVE',
				    updated_at = EXCLUDED.updated_at, version = stored_payment_method.version + 1
				RETURNING id
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", mandate.organizationId())
				.param("clientId", mandate.clientId())
				.param("paymentCustomerId", mandate.paymentCustomerId())
				.param("externalPaymentMethodId", externalPaymentMethodId)
				.param("methodType", methodType)
				.param("brand", brand)
				.param("lastFour", lastFour)
				.param("expiresMonth", expiresMonth)
				.param("expiresYear", expiresYear)
				.param("now", now)
				.query(UUID.class)
				.single();
		jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'REPLACED', updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND membership_id = :membershipId
				  AND id <> :mandateId
				  AND status IN ('ACTIVE', 'PAST_DUE', 'PAUSED')
				""")
				.param("organizationId", mandate.organizationId())
				.param("membershipId", mandate.membershipId())
				.param("mandateId", mandateId)
				.param("now", now)
				.update();
		int activated = jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET stored_payment_method_id = :methodId, status = 'ACTIVE',
				    consecutive_failures = 0, last_error = NULL,
				    updated_at = :now, version = version + 1
				WHERE id = :mandateId AND status IN ('PENDING_SETUP', 'PAST_DUE', 'PAUSED')
				""")
				.param("methodId", methodId)
				.param("mandateId", mandateId)
				.param("now", now)
				.update();
		if (activated != 1) {
			throw new IllegalStateException("The recurring mandate is no longer eligible for activation: " + mandateId);
		}
		return mandate(mandateId).orElseThrow();
	}

	public Optional<MandateRecord> revokeCurrentMandate(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String reason
	) {
		OffsetDateTime now = OffsetDateTime.now();
		Optional<MandateRecord> revoked = jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'REVOKED', revoked_at = :now, last_error = :reason,
				    updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND client_id = :clientId
				  AND membership_id = :membershipId
				  AND status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED')
				RETURNING id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("membershipId", membershipId)
				.param("reason", reason)
				.param("now", now)
				.query(UUID.class)
				.optional()
				.flatMap(this::mandate);
		revoked.ifPresent(mandate -> cancelUnstartedChargesForMandate(mandate.id(), reason));
		return revoked;
	}

	public int materializeDueCharges(LocalDate businessDate, int limit) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO recurring_charge_occurrence (
				    id, organization_id, branch_id, mandate_id, membership_id,
				    due_on, status, attempt_number, next_attempt_at,
				    created_at, updated_at, version
				)
				SELECT gen_random_uuid(), mandate.organization_id, mandate.branch_id,
				       mandate.id, mandate.membership_id, mandate.next_charge_on,
				       'PENDING', 0, :now, :now, :now, 0
				FROM recurring_payment_mandate mandate
				WHERE mandate.status IN ('ACTIVE', 'PAST_DUE')
				  AND mandate.stored_payment_method_id IS NOT NULL
				  AND mandate.next_charge_on <= :businessDate
				ORDER BY mandate.next_charge_on, mandate.created_at
				LIMIT :limit
				ON CONFLICT (mandate_id, due_on) DO NOTHING
				""")
				.param("businessDate", businessDate)
				.param("limit", limit)
				.param("now", now)
				.update();
	}

	public List<ChargeRecord> dueCharges(OffsetDateTime now, int limit) {
		return jdbcClient.sql(chargeSelect() + """
				 WHERE charge.status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
				   AND mandate.status IN ('ACTIVE', 'PAST_DUE')
				   AND charge.next_attempt_at <= :now
				   AND (charge.locked_until IS NULL OR charge.locked_until < :now)
				   AND EXISTS (
				       SELECT 1 FROM organization_payment_configuration configuration
				       WHERE configuration.organization_id = charge.organization_id
				         AND configuration.stripe_self_checkout_enabled = TRUE
				         AND configuration.stripe_recurring_enabled = TRUE
				         AND configuration.stripe_connected_at IS NOT NULL
				   )
				 ORDER BY charge.next_attempt_at, charge.created_at
				 LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query(this::mapCharge)
				.list();
	}

	public boolean claimCharge(UUID chargeId, OffsetDateTime now, OffsetDateTime lockedUntil) {
		return jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = 'PROCESSING', attempt_number = attempt_number + 1,
				    locked_until = :lockedUntil, updated_at = :now, version = version + 1
				WHERE id = :chargeId
				  AND status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
				  AND next_attempt_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				""")
				.param("chargeId", chargeId)
				.param("now", now)
				.param("lockedUntil", lockedUntil)
				.update() == 1;
	}

	public Optional<ChargeRecord> charge(UUID chargeId) {
		return jdbcClient.sql(chargeSelect() + " WHERE charge.id = :chargeId")
				.param("chargeId", chargeId)
				.query(this::mapCharge)
				.optional();
	}

	public boolean attachSale(UUID chargeId, UUID saleId, UUID paymentId, long amountCents, String currency) {
		return jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET sale_id = :saleId, payment_id = :paymentId,
				    amount_cents = :amountCents, currency = :currency,
				    updated_at = :now, version = version + 1
				WHERE id = :chargeId
				  AND status = 'PROCESSING'
				  AND EXISTS (
				      SELECT 1 FROM recurring_payment_mandate mandate
				      WHERE mandate.id = recurring_charge_occurrence.mandate_id
				        AND mandate.status IN ('ACTIVE', 'PAST_DUE')
				  )
				""")
				.param("chargeId", chargeId)
				.param("saleId", saleId)
				.param("paymentId", paymentId)
				.param("amountCents", amountCents)
				.param("currency", currency)
				.param("now", OffsetDateTime.now())
				.update() == 1;
	}

	public void awaitProvider(UUID chargeId, UUID operationId) {
		jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET provider_operation_id = :operationId, status = 'AWAITING_PROVIDER',
				    locked_until = NULL, updated_at = :now, version = version + 1
				WHERE id = :chargeId
				""")
				.param("chargeId", chargeId)
				.param("operationId", operationId)
				.param("now", OffsetDateTime.now())
				.update();
	}

	public ChargeRecord failCharge(UUID chargeId, String error, int maximumAttempts, OffsetDateTime nextAttemptAt) {
		ChargeRecord current = charge(chargeId).orElseThrow();
		boolean exhausted = current.attemptNumber() >= maximumAttempts;
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = :status, next_attempt_at = :nextAttemptAt,
				    locked_until = NULL, last_error = :error,
				    completed_at = CASE WHEN :exhausted THEN :now ELSE NULL END,
				    updated_at = :now, version = version + 1
				WHERE id = :chargeId AND status <> 'SUCCEEDED'
				""")
				.param("status", exhausted ? "FAILED" : "RETRY_SCHEDULED")
				.param("nextAttemptAt", nextAttemptAt)
				.param("error", error)
				.param("exhausted", exhausted)
				.param("now", now)
				.param("chargeId", chargeId)
				.update();
		jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'PAST_DUE', consecutive_failures = consecutive_failures + 1,
				    last_error = :error, updated_at = :now, version = version + 1
				WHERE id = :mandateId AND status IN ('ACTIVE', 'PAST_DUE')
				""")
				.param("mandateId", current.mandateId())
				.param("error", error)
				.param("now", now)
				.update();
		return charge(chargeId).orElseThrow();
	}

	public int cancelUnstartedChargesForMandate(UUID mandateId, String reason) {
		return jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = 'CANCELLED', locked_until = NULL, last_error = :reason,
				    completed_at = :now, updated_at = :now, version = version + 1
				WHERE mandate_id = :mandateId
				  AND status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED')
				""")
				.param("mandateId", mandateId)
				.param("reason", reason)
				.param("now", OffsetDateTime.now())
				.update();
	}

	public List<ChargeRecord> awaitingProviderCharges(UUID mandateId) {
		return jdbcClient.sql(chargeSelect() + """
				 WHERE charge.mandate_id = :mandateId
				   AND charge.status = 'AWAITING_PROVIDER'
				   AND charge.provider_operation_id IS NOT NULL
				 ORDER BY charge.created_at
				""")
				.param("mandateId", mandateId)
				.query(this::mapCharge)
				.list();
	}

	public boolean cancelAwaitingCharge(UUID chargeId, String reason) {
		return jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = 'CANCELLED', locked_until = NULL, last_error = :reason,
				    completed_at = :now, updated_at = :now, version = version + 1
				WHERE id = :chargeId AND status = 'AWAITING_PROVIDER'
				""")
				.param("chargeId", chargeId)
				.param("reason", reason)
				.param("now", OffsetDateTime.now())
				.update() == 1;
	}

	public boolean paymentMethodUsedByAnotherCurrentMandate(UUID storedPaymentMethodId, UUID mandateId) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1
				    FROM recurring_payment_mandate
				    WHERE stored_payment_method_id = :storedPaymentMethodId
				      AND id <> :mandateId
				      AND status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED')
				)
				""")
				.param("storedPaymentMethodId", storedPaymentMethodId)
				.param("mandateId", mandateId)
				.query(Boolean.class)
				.single();
	}

	public Optional<ChargeRecord> completeChargeForOperation(UUID operationId) {
		Optional<UUID> chargeId = jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = 'SUCCEEDED', locked_until = NULL, last_error = NULL,
				    completed_at = :now, updated_at = :now, version = version + 1
				WHERE provider_operation_id = :operationId
				  AND status <> 'SUCCEEDED'
				RETURNING id
				""")
				.param("operationId", operationId)
				.param("now", OffsetDateTime.now())
				.query(UUID.class)
				.optional();
		return chargeId.flatMap(this::charge);
	}

	public Optional<ChargeRecord> completeChargeWithoutProvider(UUID chargeId) {
		Optional<UUID> completedId = jdbcClient.sql("""
				UPDATE recurring_charge_occurrence
				SET status = 'SUCCEEDED', locked_until = NULL, last_error = NULL,
				    completed_at = :now, updated_at = :now, version = version + 1
				WHERE id = :chargeId AND status <> 'SUCCEEDED'
				RETURNING id
				""")
				.param("chargeId", chargeId)
				.param("now", OffsetDateTime.now())
				.query(UUID.class)
				.optional();
		return completedId.flatMap(this::charge);
	}

	public Optional<ChargeRecord> chargeForOperation(UUID operationId) {
		return jdbcClient.sql(chargeSelect() + " WHERE charge.provider_operation_id = :operationId")
				.param("operationId", operationId)
				.query(this::mapCharge)
				.optional();
	}

	public void advanceAfterRenewal(UUID organizationId, UUID membershipId, LocalDate nextChargeOn) {
		jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'ACTIVE', next_charge_on = :nextChargeOn,
				    consecutive_failures = 0, last_error = NULL,
				    updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND membership_id = :membershipId
				  AND status IN ('ACTIVE', 'PAST_DUE')
				""")
				.param("organizationId", organizationId)
				.param("membershipId", membershipId)
				.param("nextChargeOn", nextChargeOn)
				.param("now", OffsetDateTime.now())
				.update();
	}

	public void revokeAfterPlanChange(UUID organizationId, UUID membershipId) {
		List<UUID> mandateIds = jdbcClient.sql("""
				SELECT id
				FROM recurring_payment_mandate
				WHERE organization_id = :organizationId
				  AND membership_id = :membershipId
				  AND status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED')
				""")
				.param("organizationId", organizationId)
				.param("membershipId", membershipId)
				.query(UUID.class)
				.list();
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE recurring_payment_mandate
				SET status = 'REVOKED', revoked_at = :now,
				    last_error = 'La membresia cambio de plan; se requiere un nuevo consentimiento.',
				    updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND membership_id = :membershipId
				  AND status IN ('PENDING_SETUP', 'ACTIVE', 'PAST_DUE', 'PAUSED')
				""")
				.param("organizationId", organizationId)
				.param("membershipId", membershipId)
				.param("now", now)
				.update();
		mandateIds.forEach(id -> cancelUnstartedChargesForMandate(
				id, "La membresia cambio de plan; se requiere un nuevo consentimiento."
		));
	}

	public PageResponse<MandateHistoryRecord> mandateHistory(UUID organizationId, int page, int size) {
		long total = jdbcClient.sql("""
				SELECT COUNT(*) FROM recurring_payment_mandate
				WHERE organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query(Long.class)
				.single();
		List<MandateHistoryRecord> items = jdbcClient.sql("""
				SELECT mandate.id, mandate.branch_id, mandate.client_id, mandate.membership_id,
				       mandate.plan_id, mandate.provider, mandate.status,
				       method.brand, method.last_four, method.expires_month, method.expires_year,
				       mandate.terms_version, mandate.consented_at, mandate.consent_origin,
				       mandate.next_charge_on, mandate.consecutive_failures, mandate.last_error,
				       mandate.revoked_at, mandate.created_at, mandate.updated_at
				FROM recurring_payment_mandate mandate
				LEFT JOIN stored_payment_method method ON method.id = mandate.stored_payment_method_id
				WHERE mandate.organization_id = :organizationId
				ORDER BY mandate.created_at DESC, mandate.id DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("size", size)
				.param("offset", page * size)
				.query((rs, row) -> new MandateHistoryRecord(
						rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getObject("client_id", UUID.class), rs.getObject("membership_id", UUID.class),
						rs.getObject("plan_id", UUID.class), rs.getString("provider"), rs.getString("status"),
						rs.getString("brand"), rs.getString("last_four"),
						(Integer) rs.getObject("expires_month"), (Integer) rs.getObject("expires_year"),
						rs.getString("terms_version"), rs.getObject("consented_at", OffsetDateTime.class),
						rs.getString("consent_origin"), rs.getObject("next_charge_on", LocalDate.class),
						rs.getInt("consecutive_failures"), rs.getString("last_error"),
						rs.getObject("revoked_at", OffsetDateTime.class),
						rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
		return PageResponse.of(items, page, size, total);
	}

	public PageResponse<ChargeHistoryRecord> chargeHistory(UUID organizationId, int page, int size) {
		long total = jdbcClient.sql("""
				SELECT COUNT(*) FROM recurring_charge_occurrence
				WHERE organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query(Long.class)
				.single();
		List<ChargeHistoryRecord> items = jdbcClient.sql("""
				SELECT charge.id, charge.branch_id, charge.mandate_id, charge.membership_id,
				       mandate.client_id, mandate.plan_id, charge.due_on, charge.status,
				       charge.sale_id, charge.payment_id, charge.provider_operation_id,
				       charge.amount_cents, charge.currency, charge.attempt_number,
				       charge.next_attempt_at, charge.locked_until, charge.last_error,
				       charge.completed_at, charge.created_at, charge.updated_at
				FROM recurring_charge_occurrence charge
				JOIN recurring_payment_mandate mandate ON mandate.id = charge.mandate_id
				WHERE charge.organization_id = :organizationId
				ORDER BY charge.created_at DESC, charge.id DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("size", size)
				.param("offset", page * size)
				.query((rs, row) -> new ChargeHistoryRecord(
						rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getObject("mandate_id", UUID.class), rs.getObject("membership_id", UUID.class),
						rs.getObject("client_id", UUID.class), rs.getObject("plan_id", UUID.class),
						rs.getObject("due_on", LocalDate.class), rs.getString("status"),
						rs.getObject("sale_id", UUID.class), rs.getObject("payment_id", UUID.class),
						rs.getObject("provider_operation_id", UUID.class), (Long) rs.getObject("amount_cents"),
						rs.getString("currency"), rs.getInt("attempt_number"),
						rs.getObject("next_attempt_at", OffsetDateTime.class),
						rs.getObject("locked_until", OffsetDateTime.class), rs.getString("last_error"),
						rs.getObject("completed_at", OffsetDateTime.class),
						rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
		return PageResponse.of(items, page, size, total);
	}

	public RecurringOperationsRecord operations(UUID organizationId, OffsetDateTime now) {
		return jdbcClient.sql("""
				SELECT
				  (SELECT COUNT(*) FROM recurring_payment_mandate
				   WHERE organization_id = :organizationId AND status = 'PAST_DUE') AS past_due_mandates,
				  (SELECT COUNT(*) FROM recurring_payment_mandate
				   WHERE organization_id = :organizationId AND status = 'PAUSED') AS paused_mandates,
				  (SELECT COUNT(*) FROM recurring_charge_occurrence
				   WHERE organization_id = :organizationId AND status = 'RETRY_SCHEDULED') AS retry_charges,
				  (SELECT COUNT(*) FROM recurring_charge_occurrence
				   WHERE organization_id = :organizationId AND status = 'FAILED') AS failed_charges,
				  (SELECT COUNT(*) FROM recurring_charge_occurrence
				   WHERE organization_id = :organizationId AND status = 'PROCESSING'
				     AND locked_until < :now) AS expired_charge_leases
				""")
				.param("organizationId", organizationId)
				.param("now", now)
				.query((rs, row) -> new RecurringOperationsRecord(
						rs.getLong("past_due_mandates"), rs.getLong("paused_mandates"),
						rs.getLong("retry_charges"), rs.getLong("failed_charges"),
						rs.getLong("expired_charge_leases")
				))
				.single();
	}

	private JdbcClient.StatementSpec mandateQuery(String where) {
		return jdbcClient.sql("""
				SELECT mandate.id, mandate.organization_id, mandate.branch_id, mandate.client_id,
				       mandate.membership_id, mandate.plan_id, mandate.payment_customer_id,
				       customer.external_customer_id, mandate.stored_payment_method_id,
				       method.external_payment_method_id, mandate.provider, mandate.status,
				       method.brand, method.last_four, method.expires_month, method.expires_year,
				       mandate.terms_version, mandate.consent_text, mandate.amount_rule,
				       mandate.consented_at, mandate.consent_origin, mandate.idempotency_key,
				       mandate.next_charge_on, mandate.consecutive_failures,
				       mandate.last_error, mandate.revoked_at
				FROM recurring_payment_mandate mandate
				JOIN payment_customer customer ON customer.id = mandate.payment_customer_id
				LEFT JOIN stored_payment_method method ON method.id = mandate.stored_payment_method_id
				""" + where);
	}

	private MandateRecord mapMandate(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new MandateRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("client_id", UUID.class),
				rs.getObject("membership_id", UUID.class), rs.getObject("plan_id", UUID.class),
				rs.getObject("payment_customer_id", UUID.class), rs.getString("external_customer_id"),
				rs.getObject("stored_payment_method_id", UUID.class), rs.getString("external_payment_method_id"),
				rs.getString("provider"), rs.getString("status"), rs.getString("brand"),
				rs.getString("last_four"), (Integer) rs.getObject("expires_month"),
				(Integer) rs.getObject("expires_year"), rs.getString("terms_version"),
				rs.getString("consent_text"), rs.getString("amount_rule"),
				rs.getObject("consented_at", OffsetDateTime.class), rs.getString("consent_origin"),
				rs.getString("idempotency_key"), rs.getObject("next_charge_on", LocalDate.class),
				rs.getInt("consecutive_failures"), rs.getString("last_error"),
				rs.getObject("revoked_at", OffsetDateTime.class)
		);
	}

	private String chargeSelect() {
		return """
				SELECT charge.id, charge.organization_id, charge.branch_id, charge.mandate_id,
				       charge.membership_id, mandate.plan_id, mandate.client_id,
				       mandate.payment_customer_id, customer.external_customer_id,
				       mandate.stored_payment_method_id, method.external_payment_method_id,
				       charge.due_on, charge.status, charge.sale_id, charge.payment_id,
				       charge.provider_operation_id, charge.amount_cents, charge.currency,
				       charge.attempt_number, charge.next_attempt_at, charge.locked_until,
				       charge.last_error
				FROM recurring_charge_occurrence charge
				JOIN recurring_payment_mandate mandate ON mandate.id = charge.mandate_id
				JOIN payment_customer customer ON customer.id = mandate.payment_customer_id
				JOIN stored_payment_method method ON method.id = mandate.stored_payment_method_id
				""";
	}

	private ChargeRecord mapCharge(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new ChargeRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("mandate_id", UUID.class),
				rs.getObject("membership_id", UUID.class), rs.getObject("plan_id", UUID.class),
				rs.getObject("client_id", UUID.class), rs.getObject("payment_customer_id", UUID.class),
				rs.getString("external_customer_id"), rs.getObject("stored_payment_method_id", UUID.class),
				rs.getString("external_payment_method_id"), rs.getObject("due_on", LocalDate.class),
				rs.getString("status"), rs.getObject("sale_id", UUID.class),
				rs.getObject("payment_id", UUID.class), rs.getObject("provider_operation_id", UUID.class),
				(Long) rs.getObject("amount_cents"), rs.getString("currency"),
				rs.getInt("attempt_number"), rs.getObject("next_attempt_at", OffsetDateTime.class),
				rs.getObject("locked_until", OffsetDateTime.class), rs.getString("last_error")
		);
	}

	public record CustomerRecord(
			UUID id, UUID organizationId, UUID clientId, String provider, String externalCustomerId
	) {}

	public record CreateMandateData(
			UUID organizationId, UUID branchId, UUID clientId, UUID membershipId, UUID planId,
			UUID paymentCustomerId, String termsVersion, String consentText, String amountRule,
			OffsetDateTime consentedAt, String consentOrigin, String idempotencyKey, LocalDate nextChargeOn
	) {}

	public record MandateRecord(
			UUID id, UUID organizationId, UUID branchId, UUID clientId, UUID membershipId, UUID planId,
			UUID paymentCustomerId, String externalCustomerId, UUID storedPaymentMethodId,
			String externalPaymentMethodId, String provider, String status, String brand, String lastFour,
			Integer expiresMonth, Integer expiresYear, String termsVersion, String consentText,
			String amountRule, OffsetDateTime consentedAt, String consentOrigin, String idempotencyKey,
			LocalDate nextChargeOn, int consecutiveFailures, String lastError, OffsetDateTime revokedAt
	) {}

	public record ChargeRecord(
			UUID id, UUID organizationId, UUID branchId, UUID mandateId, UUID membershipId, UUID planId,
			UUID clientId, UUID paymentCustomerId, String externalCustomerId, UUID storedPaymentMethodId,
			String externalPaymentMethodId, LocalDate dueOn, String status, UUID saleId, UUID paymentId,
			UUID providerOperationId, Long amountCents, String currency, int attemptNumber,
			OffsetDateTime nextAttemptAt, OffsetDateTime lockedUntil, String lastError
	) {}

	public record MandateHistoryRecord(
			UUID id, UUID branchId, UUID clientId, UUID membershipId, UUID planId,
			String provider, String status, String brand, String lastFour,
			Integer expiresMonth, Integer expiresYear, String termsVersion,
			OffsetDateTime consentedAt, String consentOrigin, LocalDate nextChargeOn,
			int consecutiveFailures, String lastError, OffsetDateTime revokedAt,
			OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {}

	public record ChargeHistoryRecord(
			UUID id, UUID branchId, UUID mandateId, UUID membershipId, UUID clientId, UUID planId,
			LocalDate dueOn, String status, UUID saleId, UUID paymentId, UUID providerOperationId,
			Long amountCents, String currency, int attemptNumber, OffsetDateTime nextAttemptAt,
			OffsetDateTime lockedUntil, String lastError, OffsetDateTime completedAt,
			OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {}

	public record RecurringOperationsRecord(
			long pastDueMandates, long pausedMandates, long retryCharges,
			long failedCharges, long expiredChargeLeases
	) {}
}
