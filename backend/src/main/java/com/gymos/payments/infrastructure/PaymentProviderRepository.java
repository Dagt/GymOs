package com.gymos.payments.infrastructure;

import com.gymos.common.api.PageResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PaymentProviderRepository {
	private final JdbcClient jdbcClient;

	public PaymentProviderRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<ConnectionRecord> connection(UUID organizationId, String provider) {
		return jdbcClient.sql("""
				SELECT organization_id, provider, status, external_account_id,
				       access_token_encrypted, refresh_token_encrypted, token_expires_at,
				       metadata_json::text AS metadata_json, last_error, connected_at
				FROM payment_provider_connection
				WHERE organization_id = :organizationId AND provider = :provider
				""")
				.param("organizationId", organizationId)
				.param("provider", provider)
				.query((rs, row) -> new ConnectionRecord(
						rs.getObject("organization_id", UUID.class),
						rs.getString("provider"),
						rs.getString("status"),
						rs.getString("external_account_id"),
						rs.getString("access_token_encrypted"),
						rs.getString("refresh_token_encrypted"),
						rs.getObject("token_expires_at", OffsetDateTime.class),
						rs.getString("metadata_json"),
						rs.getString("last_error"),
						rs.getObject("connected_at", OffsetDateTime.class)
				))
				.optional();
	}

	public void saveStripeAccount(UUID organizationId, UUID actorUserId, String accountId, boolean connected, String metadataJson) {
		OffsetDateTime now = OffsetDateTime.now();
		upsertConnection(organizationId, actorUserId, "STRIPE", connected ? "CONNECTED" : "PENDING_ONBOARDING",
				accountId, null, null, null, metadataJson, null, connected ? now : null, now);
		jdbcClient.sql("""
				UPDATE organization_payment_configuration
				SET stripe_account_id = :accountId,
				    stripe_connected_at = :connectedAt,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				""")
				.param("accountId", accountId)
				.param("connectedAt", connected ? now : null)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.update();
	}

	public void saveMercadoPagoConnection(
			UUID organizationId,
			UUID actorUserId,
			String accountId,
			String accessTokenEncrypted,
			String refreshTokenEncrypted,
			OffsetDateTime tokenExpiresAt,
			String metadataJson
	) {
		OffsetDateTime now = OffsetDateTime.now();
		upsertConnection(organizationId, actorUserId, "MERCADO_PAGO", "CONNECTED", accountId,
				accessTokenEncrypted, refreshTokenEncrypted, tokenExpiresAt, metadataJson, null, now, now);
		jdbcClient.sql("""
				UPDATE organization_payment_configuration
				SET mercado_pago_account_id = :accountId,
				    mercado_pago_connected_at = :connectedAt,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				""")
				.param("accountId", accountId)
				.param("connectedAt", now)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.update();
	}

	public void updateMercadoPagoTokens(UUID organizationId, String accessTokenEncrypted, String refreshTokenEncrypted, OffsetDateTime expiresAt, String metadataJson) {
		jdbcClient.sql("""
				UPDATE payment_provider_connection
				SET access_token_encrypted = :accessToken,
				    refresh_token_encrypted = :refreshToken,
				    token_expires_at = :expiresAt,
				    metadata_json = CAST(:metadataJson AS jsonb),
				    status = 'CONNECTED', last_error = NULL,
				    updated_at = :updatedAt, version = version + 1
				WHERE organization_id = :organizationId AND provider = 'MERCADO_PAGO'
				""")
				.param("accessToken", accessTokenEncrypted)
				.param("refreshToken", refreshTokenEncrypted)
				.param("expiresAt", expiresAt)
				.param("metadataJson", metadataJson)
				.param("updatedAt", OffsetDateTime.now())
				.param("organizationId", organizationId)
				.update();
	}

	private void upsertConnection(
			UUID organizationId, UUID actorUserId, String provider, String status, String externalAccountId,
			String accessToken, String refreshToken, OffsetDateTime tokenExpiresAt, String metadataJson,
			String lastError, OffsetDateTime connectedAt, OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO payment_provider_connection (
				    organization_id, provider, status, external_account_id,
				    access_token_encrypted, refresh_token_encrypted, token_expires_at,
				    metadata_json, last_error, connected_at, created_at, updated_at, updated_by, version
				)
				VALUES (
				    :organizationId, :provider, :status, :externalAccountId,
				    :accessToken, :refreshToken, :tokenExpiresAt,
				    CAST(:metadataJson AS jsonb), :lastError, :connectedAt, :now, :now, :updatedBy, 0
				)
				ON CONFLICT (organization_id, provider) DO UPDATE SET
				    status = EXCLUDED.status,
				    external_account_id = EXCLUDED.external_account_id,
				    access_token_encrypted = COALESCE(EXCLUDED.access_token_encrypted, payment_provider_connection.access_token_encrypted),
				    refresh_token_encrypted = COALESCE(EXCLUDED.refresh_token_encrypted, payment_provider_connection.refresh_token_encrypted),
				    token_expires_at = COALESCE(EXCLUDED.token_expires_at, payment_provider_connection.token_expires_at),
				    metadata_json = EXCLUDED.metadata_json,
				    last_error = EXCLUDED.last_error,
				    connected_at = EXCLUDED.connected_at,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = payment_provider_connection.version + 1
				""")
				.param("organizationId", organizationId)
				.param("provider", provider)
				.param("status", status)
				.param("externalAccountId", externalAccountId)
				.param("accessToken", accessToken)
				.param("refreshToken", refreshToken)
				.param("tokenExpiresAt", tokenExpiresAt)
				.param("metadataJson", metadataJson == null ? "{}" : metadataJson)
				.param("lastError", lastError)
				.param("connectedAt", connectedAt)
				.param("now", now)
				.param("updatedBy", actorUserId)
				.update();
	}

	public void createOauthRequest(String stateHash, UUID organizationId, UUID actorUserId, String provider, String codeVerifierEncrypted, OffsetDateTime expiresAt) {
		jdbcClient.sql("""
				INSERT INTO payment_provider_oauth_request (
				    state_hash, organization_id, provider, actor_user_id,
				    code_verifier_encrypted, expires_at, created_at
				)
				VALUES (:stateHash, :organizationId, :provider, :actorUserId, :codeVerifier, :expiresAt, :createdAt)
				""")
				.param("stateHash", stateHash)
				.param("organizationId", organizationId)
				.param("provider", provider)
				.param("actorUserId", actorUserId)
				.param("codeVerifier", codeVerifierEncrypted)
				.param("expiresAt", expiresAt)
				.param("createdAt", OffsetDateTime.now())
				.update();
	}

	public Optional<OauthRequestRecord> consumeOauthRequest(String stateHash, String provider, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE payment_provider_oauth_request
				SET consumed_at = :now
				WHERE state_hash = :stateHash
				  AND provider = :provider
				  AND consumed_at IS NULL
				  AND expires_at >= :now
				RETURNING organization_id, actor_user_id, code_verifier_encrypted
				""")
				.param("stateHash", stateHash)
				.param("provider", provider)
				.param("now", now)
				.query((rs, row) -> new OauthRequestRecord(
						rs.getObject("organization_id", UUID.class),
						rs.getObject("actor_user_id", UUID.class),
						rs.getString("code_verifier_encrypted")
				))
				.optional();
	}

	public UUID createOperation(CreateOperationData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID id = UUID.randomUUID();
		return jdbcClient.sql("""
				INSERT INTO payment_provider_operation (
				    id, organization_id, branch_id, provider, operation_type,
				    sale_id, payment_id, client_id, mandate_id, recurring_charge_id,
				    idempotency_key, status,
				    amount_cents, currency, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :provider, :operationType,
				    :saleId, :paymentId, :clientId, :mandateId, :recurringChargeId,
				    :idempotencyKey, 'CREATING',
				    :amountCents, :currency, :now, :now, 0
				)
				ON CONFLICT (organization_id, provider, idempotency_key) DO UPDATE
				SET updated_at = payment_provider_operation.updated_at
				RETURNING id
				""")
				.param("id", id)
				.param("organizationId", data.organizationId())
				.param("branchId", data.branchId())
				.param("provider", data.provider())
				.param("operationType", data.operationType())
				.param("saleId", data.saleId())
				.param("paymentId", data.paymentId())
				.param("clientId", data.clientId())
				.param("mandateId", data.mandateId())
				.param("recurringChargeId", data.recurringChargeId())
				.param("idempotencyKey", data.idempotencyKey())
				.param("amountCents", data.amountCents())
				.param("currency", data.currency())
				.param("now", now)
				.query(UUID.class)
				.single();
	}

	public Optional<OperationRecord> operation(UUID operationId) {
		return operationQuery("WHERE id = :value", operationId);
	}

	public Optional<OperationRecord> operationByExternalId(String provider, String externalId) {
		return jdbcClient.sql(operationSelect() + " WHERE provider = :provider AND external_operation_id = :externalId")
				.param("provider", provider)
				.param("externalId", externalId)
				.query(this::mapOperation)
				.optional();
	}

	public Optional<OperationRecord> latestOperationForPayment(UUID organizationId, String provider, String operationType, UUID paymentId) {
		return jdbcClient.sql(operationSelect() + """
				 WHERE organization_id = :organizationId
				   AND provider = :provider
				   AND operation_type = :operationType
				   AND payment_id = :paymentId
				 ORDER BY created_at DESC, id DESC
				 LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("provider", provider)
				.param("operationType", operationType)
				.param("paymentId", paymentId)
				.query(this::mapOperation)
				.optional();
	}

	public String checkoutPaymentState(CreateOperationData data) {
		return checkoutPaymentState(data, false);
	}

	private String checkoutPaymentState(CreateOperationData data, boolean lockSale) {
		return jdbcClient.sql("""
				SELECT CASE
				    WHEN payment.status = 'COMPLETED' AND sale.status = 'PAID' THEN 'COMPLETED'
				    WHEN payment.status = 'PENDING_VALIDATION' AND sale.status = 'PENDING_PAYMENT'
				         AND sale.balance_cents = payment.amount_cents THEN 'PENDING_VALIDATION'
				    ELSE 'AWAITING_PAYMENT'
				END
				FROM sale JOIN payment ON payment.sale_id = sale.id
				  AND payment.organization_id = sale.organization_id AND payment.branch_id = sale.branch_id
				  AND payment.client_id = sale.client_id
				WHERE sale.id = :sale AND sale.organization_id = :org AND sale.branch_id = :branch
				  AND sale.client_id = :client AND payment.id = :payment AND payment.method = 'CARD_ONLINE'
				  AND payment.amount_cents = :amount AND payment.currency = :currency
				  AND sale.deleted_at IS NULL AND payment.deleted_at IS NULL
				""" + (lockSale ? " FOR UPDATE OF sale" : ""))
				.param("sale", data.saleId()).param("org", data.organizationId()).param("branch", data.branchId())
				.param("client", data.clientId()).param("payment", data.paymentId())
				.param("amount", data.amountCents()).param("currency", data.currency())
				.query(String.class).optional().orElse("AWAITING_PAYMENT");
	}

	@Transactional
	public CheckoutReservation reserveCheckout(CreateOperationData data, UUID expectedLatest) {
		// Serialize reservations with payment completion on the sale row. No provider I/O under this lock.
		String state = checkoutPaymentState(data, true);
		if (!"PENDING_VALIDATION".equals(state)) return new CheckoutReservation(state, null);
		var latest = latestOperationForPayment(data.organizationId(), data.provider(), data.operationType(), data.paymentId()).orElse(null);
		UUID latestId = latest == null ? null : latest.id();
		if (!java.util.Objects.equals(latestId, expectedLatest)) return new CheckoutReservation(state, null);
		if (latest != null && "COMPLETED".equals(latest.status())) return new CheckoutReservation("AWAITING_PAYMENT", null);
		return new CheckoutReservation(state, operation(createOperation(data)).orElseThrow());
	}

	public boolean checkoutCreationReplayAllowed(UUID operationId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM payment_provider_operation
				               WHERE id = :id AND created_at > CURRENT_TIMESTAMP - INTERVAL '23 hours')
				""").param("id", operationId).query(Boolean.class).single();
	}

	public void requestCheckoutReconciliation(UUID operationId) {
		jdbcClient.sql("""
				UPDATE payment_provider_operation SET status = 'AWAITING_PAYMENT', version = version + 1
				WHERE id = :id AND provider = 'STRIPE' AND operation_type = 'CHECKOUT_SESSION'
				  AND external_operation_id IS NOT NULL AND status IN ('OPEN', 'EXPIRED', 'FAILED')
				""").param("id", operationId).update();
	}

	public record CheckoutReservation(String paymentState, OperationRecord operation) {}

	public Optional<OperationRecord> latestOperationForMandate(
			UUID organizationId,
			String provider,
			String operationType,
			UUID mandateId
	) {
		return jdbcClient.sql(operationSelect() + """
				 WHERE organization_id = :organizationId
				   AND provider = :provider
				   AND operation_type = :operationType
				   AND mandate_id = :mandateId
				 ORDER BY created_at DESC, id DESC
				 LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("provider", provider)
				.param("operationType", operationType)
				.param("mandateId", mandateId)
				.query(this::mapOperation)
				.optional();
	}

	public Optional<OperationRecord> latestOperationForRecurringCharge(
			UUID organizationId,
			UUID recurringChargeId
	) {
		return jdbcClient.sql(operationSelect() + """
				 WHERE organization_id = :organizationId
				   AND provider = 'STRIPE'
				   AND operation_type = 'RECURRING_PAYMENT_INTENT'
				   AND recurring_charge_id = :recurringChargeId
				 ORDER BY created_at DESC, id DESC
				 LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("recurringChargeId", recurringChargeId)
				.query(this::mapOperation)
				.optional();
	}

	public List<OperationRecord> reconcilableOperations(OffsetDateTime updatedBefore, OffsetDateTime createdAfter, int limit) {
		return jdbcClient.sql(operationSelect() + """
				 WHERE external_operation_id IS NOT NULL
				   AND status NOT IN ('COMPLETED', 'FAILED', 'AMOUNT_MISMATCH', 'CANCELLED', 'CANCELED', 'EXPIRED', 'REJECTED')
				   AND updated_at <= :updatedBefore
				   AND created_at >= :createdAfter
				 ORDER BY updated_at, created_at
				 LIMIT :limit
				""")
				.param("updatedBefore", updatedBefore)
				.param("createdAfter", createdAfter)
				.param("limit", limit)
				.query(this::mapOperation)
				.list();
	}

	private Optional<OperationRecord> operationQuery(String where, Object value) {
		return jdbcClient.sql(operationSelect() + " " + where)
				.param("value", value)
				.query(this::mapOperation)
				.optional();
	}

	public void markOperationCreated(UUID operationId, String externalId, String status, String checkoutUrl, String payloadJson, OffsetDateTime expiresAt) {
		jdbcClient.sql("""
				UPDATE payment_provider_operation
				SET external_operation_id = :externalId, status = :status,
				    checkout_url = :checkoutUrl,
				    provider_payload_json = CAST(:payloadJson AS jsonb),
				    expires_at = :expiresAt,
				    updated_at = :updatedAt, version = version + 1
				WHERE id = :operationId AND external_operation_id IS NULL AND status <> 'COMPLETED'
				""")
				.param("externalId", externalId)
				.param("status", unverifiedStatus(status))
				.param("checkoutUrl", checkoutUrl)
				.param("payloadJson", payloadJson == null ? "{}" : payloadJson)
				.param("expiresAt", expiresAt)
				.param("updatedAt", OffsetDateTime.now())
				.param("operationId", operationId)
				.update();
	}

	public boolean markOperationStatus(UUID operationId, String status, String payloadJson, boolean completed) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				UPDATE payment_provider_operation
				SET status = :status,
				    provider_payload_json = CAST(:payloadJson AS jsonb),
				    completed_at = CASE WHEN :completed THEN :now ELSE completed_at END,
				    updated_at = :now, version = version + 1
				WHERE id = :operationId AND status <> 'COMPLETED'
				  AND NOT (provider = 'MERCADO_PAGO' AND completed_at IS NOT NULL)
				""")
				.param("status", completed ? status : unverifiedStatus(status))
				.param("payloadJson", payloadJson == null ? "{}" : payloadJson)
				.param("completed", completed)
				.param("now", now)
				.param("operationId", operationId)
				.update() == 1;
	}

	private String unverifiedStatus(String status) {
		return "COMPLETED".equalsIgnoreCase(status) ? "AWAITING_PAYMENT" : status;
	}

	public void markPointReconciliationRequired(UUID operationId) {
		jdbcClient.sql("""
				UPDATE payment_provider_operation
				SET status = 'RECONCILIATION_REQUIRED', updated_at = :now, version = version + 1
				WHERE id = :operationId AND provider = 'MERCADO_PAGO'
				  AND status <> 'COMPLETED' AND completed_at IS NULL
				""")
				.param("operationId", operationId)
				.param("now", OffsetDateTime.now())
				.update();
	}

	public void saveReceiptUrl(UUID operationId, String receiptUrl) {
		if (receiptUrl == null || receiptUrl.isBlank()) return;
		jdbcClient.sql("""
				UPDATE payment_provider_operation
				SET receipt_url = :receiptUrl, updated_at = :now, version = version + 1
				WHERE id = :operationId
				""")
				.param("operationId", operationId)
				.param("receiptUrl", receiptUrl.substring(0, Math.min(2048, receiptUrl.length())))
				.param("now", OffsetDateTime.now())
				.update();
	}

	public boolean registerWebhook(String provider, String eventId, String eventType, String payloadHash, UUID organizationId, UUID attemptId) {
		return jdbcClient.sql("""
				INSERT INTO payment_provider_webhook_event (
				    id, organization_id, provider, external_event_id, event_type,
				    payload_hash, status, received_at, processing_attempt_id, processing_started_at
				)
				VALUES (:id, :organizationId, :provider, :eventId, :eventType, :payloadHash, 'PROCESSING', :now, :attemptId, :now)
				ON CONFLICT (provider, external_event_id) DO UPDATE SET
				    status = 'PROCESSING',
				    error_message = NULL,
				    processed_at = NULL,
				    processing_attempt_id = EXCLUDED.processing_attempt_id,
				    processing_started_at = EXCLUDED.processing_started_at
				WHERE payment_provider_webhook_event.organization_id IS NOT DISTINCT FROM EXCLUDED.organization_id
				  AND payment_provider_webhook_event.event_type IS NOT DISTINCT FROM EXCLUDED.event_type
				  AND payment_provider_webhook_event.payload_hash = EXCLUDED.payload_hash
				  AND (payment_provider_webhook_event.status = 'FAILED'
				    OR (payment_provider_webhook_event.status = 'PROCESSING'
				      AND COALESCE(payment_provider_webhook_event.processing_started_at,
				                   payment_provider_webhook_event.received_at) <= :now - interval '5 minutes'))
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("provider", provider)
				.param("eventId", eventId)
				.param("eventType", eventType)
				.param("payloadHash", payloadHash)
				.param("attemptId", attemptId)
				.param("now", OffsetDateTime.now())
				.update() == 1;
	}

	public void completeWebhook(String provider, String eventId, String status, String error, UUID attemptId) {
		jdbcClient.sql("""
				UPDATE payment_provider_webhook_event
				SET status = :status, error_message = :error, processed_at = :processedAt
				WHERE provider = :provider AND external_event_id = :eventId
				  AND status = 'PROCESSING' AND processing_attempt_id = :attemptId
				""")
				.param("status", status)
				.param("error", error)
				.param("processedAt", OffsetDateTime.now())
				.param("provider", provider)
				.param("eventId", eventId)
				.param("attemptId", attemptId)
				.update();
	}

	public PageResponse<WebhookHistoryRecord> webhookHistory(UUID organizationId, int page, int size) {
		long total = jdbcClient.sql("""
				SELECT COUNT(*) FROM payment_provider_webhook_event
				WHERE organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query(Long.class)
				.single();
		List<WebhookHistoryRecord> items = jdbcClient.sql("""
				SELECT id, provider, external_event_id, event_type, status,
				       error_message, received_at, processed_at
				FROM payment_provider_webhook_event
				WHERE organization_id = :organizationId
				ORDER BY received_at DESC, id DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("size", size)
				.param("offset", page * size)
				.query((rs, row) -> new WebhookHistoryRecord(
						rs.getObject("id", UUID.class), rs.getString("provider"),
						rs.getString("external_event_id"), rs.getString("event_type"),
						rs.getString("status"), rs.getString("error_message"),
						rs.getObject("received_at", OffsetDateTime.class),
						rs.getObject("processed_at", OffsetDateTime.class)
				))
				.list();
		return PageResponse.of(items, page, size, total);
	}

	public ProviderOperationsRecord operations(UUID organizationId, OffsetDateTime staleBefore) {
		return jdbcClient.sql("""
				SELECT
				  (SELECT COUNT(*) FROM payment_provider_webhook_event
				   WHERE organization_id = :organizationId AND status = 'FAILED') AS failed_webhooks,
				  (SELECT COUNT(*) FROM payment_provider_operation
				   WHERE organization_id = :organizationId
				     AND status NOT IN ('COMPLETED', 'FAILED', 'AMOUNT_MISMATCH', 'CANCELLED', 'CANCELED', 'EXPIRED', 'REJECTED')
				     AND updated_at < :staleBefore) AS stale_operations
				""")
				.param("organizationId", organizationId)
				.param("staleBefore", staleBefore)
				.query((rs, row) -> new ProviderOperationsRecord(
						rs.getLong("failed_webhooks"), rs.getLong("stale_operations")
				))
				.single();
	}

	public List<UUID> activeOrganizationIds() {
		return jdbcClient.sql("""
				SELECT id FROM organization
				WHERE status = 'ACTIVE' AND deleted_at IS NULL
				ORDER BY id
				""")
				.query(UUID.class)
				.list();
	}

	private String operationSelect() {
		return """
				SELECT id, organization_id, branch_id, provider, operation_type,
				       sale_id, payment_id, client_id, mandate_id, recurring_charge_id,
				       external_operation_id,
				       idempotency_key, status, amount_cents, currency, checkout_url,
				       provider_payload_json::text AS provider_payload_json, expires_at
				FROM payment_provider_operation
				""";
	}

	private OperationRecord mapOperation(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new OperationRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getString("provider"), rs.getString("operation_type"),
				rs.getObject("sale_id", UUID.class), rs.getObject("payment_id", UUID.class),
				rs.getObject("client_id", UUID.class), rs.getObject("mandate_id", UUID.class),
				rs.getObject("recurring_charge_id", UUID.class), rs.getString("external_operation_id"),
				rs.getString("idempotency_key"), rs.getString("status"), rs.getLong("amount_cents"),
				rs.getString("currency"), rs.getString("checkout_url"), rs.getString("provider_payload_json"),
				rs.getObject("expires_at", OffsetDateTime.class)
		);
	}

	public record ConnectionRecord(
			UUID organizationId, String provider, String status, String externalAccountId,
			String accessTokenEncrypted, String refreshTokenEncrypted, OffsetDateTime tokenExpiresAt,
			String metadataJson, String lastError, OffsetDateTime connectedAt
	) {}

	public record OauthRequestRecord(UUID organizationId, UUID actorUserId, String codeVerifierEncrypted) {}

	public record CreateOperationData(
			UUID organizationId, UUID branchId, String provider, String operationType,
			UUID saleId, UUID paymentId, UUID clientId, String idempotencyKey,
			long amountCents, String currency, UUID mandateId, UUID recurringChargeId
	) {
		public CreateOperationData(
				UUID organizationId, UUID branchId, String provider, String operationType,
				UUID saleId, UUID paymentId, UUID clientId, String idempotencyKey,
				long amountCents, String currency
		) {
			this(
					organizationId, branchId, provider, operationType, saleId, paymentId,
					clientId, idempotencyKey, amountCents, currency, null, null
			);
		}
	}

	public record OperationRecord(
			UUID id, UUID organizationId, UUID branchId, String provider, String operationType,
			UUID saleId, UUID paymentId, UUID clientId, UUID mandateId, UUID recurringChargeId,
			String externalOperationId,
			String idempotencyKey, String status, long amountCents, String currency,
			String checkoutUrl, String providerPayloadJson, OffsetDateTime expiresAt
	) {
		public OperationRecord(
				UUID id, UUID organizationId, UUID branchId, String provider, String operationType,
				UUID saleId, UUID paymentId, UUID clientId, String externalOperationId,
				String idempotencyKey, String status, long amountCents, String currency,
				String checkoutUrl, String providerPayloadJson, OffsetDateTime expiresAt
		) {
			this(
					id, organizationId, branchId, provider, operationType, saleId, paymentId,
					clientId, null, null, externalOperationId, idempotencyKey, status,
					amountCents, currency, checkoutUrl, providerPayloadJson, expiresAt
			);
		}
	}

	public record WebhookHistoryRecord(
			UUID id, String provider, String externalEventId, String eventType,
			String status, String errorMessage, OffsetDateTime receivedAt, OffsetDateTime processedAt
	) {}

	public record ProviderOperationsRecord(long failedWebhooks, long staleOperations) {}
}
