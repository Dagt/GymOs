package com.gymos.notifications.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
	private static final String NOTIFICATION_COLUMNS = """
			id, channel, recipient, subject, body, body_html, status, category,
			recipient_type, recipient_id, trigger_type, reference_type, reference_id,
			scheduled_at, sent_at, error_message, provider_key, provider_message_id,
			attempt_count, available_at, locked_until, last_attempt_at, created_at, updated_at
			""";

	private final JdbcClient jdbcClient;

	public NotificationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<NotificationRecord> notifications(UUID organizationId, UUID branchId, int limit) {
		return jdbcClient.sql("""
				SELECT %s
				FROM notification_log
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				ORDER BY created_at DESC
				LIMIT :limit
				""".formatted(NOTIFICATION_COLUMNS))
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("limit", limit)
				.query(this::mapNotification)
				.list();
	}

	public UUID create(UUID organizationId, UUID branchId, UUID actorUserId, CreateNotificationData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID notificationId = UUID.randomUUID();
		var inserted = jdbcClient.sql("""
				INSERT INTO notification_log (
				    id, organization_id, branch_id, channel, recipient, subject, body, body_html,
				    status, category, recipient_type, recipient_id, trigger_type, reference_type,
				    reference_id, scheduled_at, idempotency_key, available_at,
				    created_at, updated_at, created_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :channel, :recipient, :subject, :body, :bodyHtml,
				    :status, :category, :recipientType, :recipientId, :triggerType, :referenceType,
				    :referenceId, :scheduledAt, :idempotencyKey, COALESCE(:scheduledAt, :createdAt),
				    :createdAt, :createdAt, :createdBy, 0
				)
				ON CONFLICT (organization_id, idempotency_key)
				    WHERE idempotency_key IS NOT NULL
				DO NOTHING
				RETURNING id
				""")
				.param("id", notificationId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("channel", data.channel())
				.param("recipient", data.recipient())
				.param("subject", data.subject())
				.param("body", data.body())
				.param("bodyHtml", data.bodyHtml())
				.param("status", data.status())
				.param("category", data.category())
				.param("recipientType", data.recipientType())
				.param("recipientId", data.recipientId())
				.param("triggerType", data.triggerType())
				.param("referenceType", data.referenceType())
				.param("referenceId", data.referenceId())
				.param("scheduledAt", data.scheduledAt())
				.param("idempotencyKey", data.idempotencyKey())
				.param("createdAt", now)
				.param("createdBy", actorUserId)
				.query(UUID.class)
				.optional();
		if (inserted.isPresent()) return inserted.get();
		return jdbcClient.sql("""
				SELECT id FROM notification_log
				WHERE organization_id = :organizationId AND idempotency_key = :idempotencyKey
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", data.idempotencyKey())
				.query(UUID.class)
				.single();
	}

	public NotificationRecord notification(UUID organizationId, UUID notificationId) {
		return jdbcClient.sql("""
				SELECT %s
				FROM notification_log
				WHERE organization_id = :organizationId AND id = :notificationId
				""".formatted(NOTIFICATION_COLUMNS))
				.param("organizationId", organizationId)
				.param("notificationId", notificationId)
				.query(this::mapNotification)
				.single();
	}

	public Optional<RecipientRecord> recipient(UUID organizationId, String recipientType, UUID recipientId) {
		if ("CLIENT".equals(recipientType)) {
			return jdbcClient.sql("""
					SELECT person.email, TRIM(person.first_name || ' ' || person.last_name) AS display_name
					FROM client
					JOIN person ON person.id = client.person_id AND person.organization_id = client.organization_id
					WHERE client.organization_id = :organizationId AND client.id = :recipientId
					  AND client.deleted_at IS NULL AND person.deleted_at IS NULL
					""").param("organizationId", organizationId).param("recipientId", recipientId)
					.query((rs, row) -> new RecipientRecord(rs.getString("email"), rs.getString("display_name")))
					.optional();
		}
		if ("USER".equals(recipientType)) {
			return jdbcClient.sql("""
					SELECT person.email, TRIM(person.first_name || ' ' || person.last_name) AS display_name
					FROM user_account
					JOIN person ON person.id = user_account.person_id AND person.organization_id = user_account.organization_id
					WHERE user_account.organization_id = :organizationId AND user_account.id = :recipientId
					  AND user_account.deleted_at IS NULL AND person.deleted_at IS NULL
					""").param("organizationId", organizationId).param("recipientId", recipientId)
					.query((rs, row) -> new RecipientRecord(rs.getString("email"), rs.getString("display_name")))
					.optional();
		}
		return Optional.empty();
	}

	public EmailConfigurationRecord emailConfiguration(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT CAST(:organizationId AS uuid) AS organization_id,
				       COALESCE(configuration.email_enabled, false) AS email_enabled,
				       COALESCE(configuration.email_provider_key, 'SMTP') AS email_provider_key,
				       configuration.from_email, configuration.from_name, configuration.reply_to,
				       configuration.updated_at
				FROM (SELECT 1) seed
				LEFT JOIN notification_channel_configuration configuration
				  ON configuration.organization_id = :organizationId
				""").param("organizationId", organizationId)
				.query(this::mapEmailConfiguration).single();
	}

	public EmailConfigurationRecord upsertEmailConfiguration(
			UUID organizationId, UUID actorUserId, boolean enabled, String providerKey,
			String fromEmail, String fromName, String replyTo
	) {
		jdbcClient.sql("""
				INSERT INTO notification_channel_configuration (
				    organization_id, email_enabled, email_provider_key, from_email,
				    from_name, reply_to, updated_at, updated_by, version
				) VALUES (
				    :organizationId, :enabled, :providerKey, :fromEmail,
				    :fromName, :replyTo, now(), :actorUserId, 0
				)
				ON CONFLICT (organization_id) DO UPDATE SET
				    email_enabled = EXCLUDED.email_enabled,
				    email_provider_key = EXCLUDED.email_provider_key,
				    from_email = EXCLUDED.from_email,
				    from_name = EXCLUDED.from_name,
				    reply_to = EXCLUDED.reply_to,
				    updated_at = now(), updated_by = EXCLUDED.updated_by,
				    version = notification_channel_configuration.version + 1
				""").param("organizationId", organizationId).param("enabled", enabled)
				.param("providerKey", providerKey).param("fromEmail", fromEmail)
				.param("fromName", fromName).param("replyTo", replyTo)
				.param("actorUserId", actorUserId).update();
		return emailConfiguration(organizationId);
	}

	public boolean emailAllowed(UUID organizationId, String recipientHash, String category) {
		return jdbcClient.sql("""
				SELECT CASE
				  WHEN preference.recipient_hash IS NULL THEN :category <> 'MARKETING'
				  WHEN NOT preference.email_enabled THEN false
				  WHEN :category = 'MARKETING' THEN preference.marketing_email_enabled
				  ELSE true
				END
				FROM (SELECT 1) seed
				LEFT JOIN notification_recipient_preference preference
				  ON preference.organization_id = :organizationId
				 AND preference.recipient_hash = :recipientHash
				""").param("organizationId", organizationId).param("recipientHash", recipientHash)
				.param("category", category).query(Boolean.class).single();
	}

	public void setEmailPreference(
			UUID organizationId, String recipientHash, boolean emailEnabled,
			boolean marketingEnabled, UUID actorUserId
	) {
		jdbcClient.sql("""
				INSERT INTO notification_recipient_preference (
				 organization_id, recipient_hash, email_enabled, marketing_email_enabled,
				 updated_at, updated_by, version
				) VALUES (
				 :organizationId, :recipientHash, :emailEnabled, :marketingEnabled,
				 now(), :actorUserId, 0
				)
				ON CONFLICT (organization_id, recipient_hash) DO UPDATE SET
				 email_enabled = EXCLUDED.email_enabled,
				 marketing_email_enabled = EXCLUDED.marketing_email_enabled,
				 updated_at = now(), updated_by = EXCLUDED.updated_by,
				 version = notification_recipient_preference.version + 1
				""").param("organizationId", organizationId).param("recipientHash", recipientHash)
				.param("emailEnabled", emailEnabled).param("marketingEnabled", marketingEnabled)
				.param("actorUserId", actorUserId).update();
	}

	public EmailPreferenceRecord emailPreference(UUID organizationId, String recipientHash) {
		return jdbcClient.sql("""
				SELECT COALESCE(preference.email_enabled, true) AS email_enabled,
				       COALESCE(preference.marketing_email_enabled, false) AS marketing_email_enabled,
				       preference.updated_at
				FROM (SELECT 1) seed
				LEFT JOIN notification_recipient_preference preference
				  ON preference.organization_id = :organizationId
				 AND preference.recipient_hash = :recipientHash
				""").param("organizationId", organizationId).param("recipientHash", recipientHash)
				.query((rs, row) -> new EmailPreferenceRecord(
						rs.getBoolean("email_enabled"), rs.getBoolean("marketing_email_enabled"),
						rs.getObject("updated_at", OffsetDateTime.class)
				)).single();
	}

	public List<UUID> dueEmailIds(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT id FROM (
				  SELECT notification.id, notification.organization_id, notification.available_at,
				         row_number() OVER (
				           PARTITION BY notification.organization_id
				           ORDER BY notification.available_at, notification.created_at, notification.id
				         ) AS tenant_rank
				  FROM notification_log notification
				  JOIN notification_channel_configuration configuration
				    ON configuration.organization_id = notification.organization_id
				   AND configuration.email_enabled
				  WHERE notification.channel = 'EMAIL'
				    AND notification.status IN ('QUEUED', 'RETRY_SCHEDULED', 'PROCESSING')
				    AND notification.available_at <= :now
				    AND (notification.locked_until IS NULL OR notification.locked_until < :now)
				) fair_queue
				ORDER BY tenant_rank, available_at, organization_id, id
				LIMIT :limit
				""").param("now", now).param("limit", limit).query(UUID.class).list();
	}

	public boolean claimEmail(UUID notificationId, OffsetDateTime now, OffsetDateTime lockedUntil) {
		return jdbcClient.sql("""
				UPDATE notification_log SET
				 status = 'PROCESSING', attempt_count = attempt_count + 1,
				 locked_until = :lockedUntil, last_attempt_at = :now,
				 updated_at = :now, version = version + 1
				WHERE id = :notificationId AND channel = 'EMAIL'
				  AND status IN ('QUEUED', 'RETRY_SCHEDULED', 'PROCESSING')
				  AND available_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				""").param("notificationId", notificationId).param("now", now)
				.param("lockedUntil", lockedUntil).update() == 1;
	}

	public Optional<EmailDeliveryRecord> emailDelivery(UUID notificationId) {
		return jdbcClient.sql("""
				SELECT notification.id, notification.organization_id, notification.recipient,
				       notification.subject, notification.body, notification.body_html,
				       notification.category, notification.idempotency_key,
				       notification.attempt_count, configuration.email_provider_key,
				       configuration.from_email, configuration.from_name, configuration.reply_to
				FROM notification_log notification
				JOIN notification_channel_configuration configuration
				  ON configuration.organization_id = notification.organization_id
				WHERE notification.id = :notificationId AND notification.status = 'PROCESSING'
				""").param("notificationId", notificationId)
				.query((rs, row) -> new EmailDeliveryRecord(
						rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
						rs.getString("recipient"), rs.getString("subject"), rs.getString("body"),
						rs.getString("body_html"), rs.getString("category"), rs.getString("idempotency_key"),
						rs.getInt("attempt_count"), rs.getString("email_provider_key"),
						rs.getString("from_email"), rs.getString("from_name"), rs.getString("reply_to")
				)).optional();
	}

	public void markSent(UUID notificationId, String providerKey, String providerMessageId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE notification_log SET status = 'SENT', provider_key = :providerKey,
				 provider_message_id = :providerMessageId, sent_at = :now,
				 locked_until = NULL, error_message = NULL, updated_at = :now, version = version + 1
				WHERE id = :notificationId AND status = 'PROCESSING'
				""").param("notificationId", notificationId).param("providerKey", providerKey)
				.param("providerMessageId", providerMessageId).param("now", now).update();
	}

	public void markSuppressed(UUID notificationId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE notification_log SET status = 'SUPPRESSED', locked_until = NULL,
				 error_message = NULL, updated_at = :now, version = version + 1
				WHERE id = :notificationId AND status = 'PROCESSING'
				""").param("notificationId", notificationId).param("now", now).update();
	}

	public void markDeliveryFailure(
			UUID notificationId, boolean retry, OffsetDateTime availableAt, String error, OffsetDateTime now
	) {
		jdbcClient.sql("""
				UPDATE notification_log SET status = :status, available_at = :availableAt,
				 locked_until = NULL, error_message = :error, updated_at = :now, version = version + 1
				WHERE id = :notificationId AND status = 'PROCESSING'
				""").param("notificationId", notificationId)
				.param("status", retry ? "RETRY_SCHEDULED" : "DEAD_LETTER")
				.param("availableAt", availableAt).param("error", error).param("now", now).update();
	}

	public boolean requeueDeadLetter(UUID organizationId, UUID notificationId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE notification_log SET status = 'QUEUED', attempt_count = 0,
				 available_at = :now, locked_until = NULL, error_message = NULL,
				 updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId AND id = :notificationId
				  AND channel = 'EMAIL' AND status = 'DEAD_LETTER'
				""").param("organizationId", organizationId).param("notificationId", notificationId)
				.param("now", now).update() == 1;
	}

	public List<NotificationOperationsRecord> notificationOperations() {
		return jdbcClient.sql("""
				SELECT organization.id AS organization_id,
				       COUNT(notification.id) FILTER (WHERE notification.status IN ('QUEUED', 'RETRY_SCHEDULED')) AS pending,
				       COUNT(notification.id) FILTER (WHERE notification.status = 'DEAD_LETTER') AS dead_letters,
				       COUNT(notification.id) FILTER (
				         WHERE notification.status = 'PROCESSING' AND notification.locked_until < now()
				       ) AS expired_leases
				FROM organization
				LEFT JOIN notification_log notification
				  ON notification.organization_id = organization.id AND notification.channel = 'EMAIL'
				WHERE organization.status = 'ACTIVE' AND organization.deleted_at IS NULL
				GROUP BY organization.id
				""").query((rs, row) -> new NotificationOperationsRecord(
					rs.getObject("organization_id", UUID.class), rs.getLong("pending"),
					rs.getLong("dead_letters"), rs.getLong("expired_leases")
			)).list();
	}

	private EmailConfigurationRecord mapEmailConfiguration(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new EmailConfigurationRecord(
				rs.getObject("organization_id", UUID.class), rs.getBoolean("email_enabled"),
				rs.getString("email_provider_key"), rs.getString("from_email"),
				rs.getString("from_name"), rs.getString("reply_to"),
				rs.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private NotificationRecord mapNotification(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new NotificationRecord(
				rs.getObject("id", UUID.class), rs.getString("channel"), maskedRecipient(rs.getString("recipient")),
				rs.getString("subject"), rs.getString("body"), rs.getString("body_html"),
				rs.getString("status"), rs.getString("category"), rs.getString("recipient_type"),
				rs.getObject("recipient_id", UUID.class), rs.getString("trigger_type"),
				rs.getString("reference_type"), rs.getObject("reference_id", UUID.class),
				rs.getObject("scheduled_at", OffsetDateTime.class), rs.getObject("sent_at", OffsetDateTime.class),
				rs.getString("error_message"), rs.getString("provider_key"),
				rs.getString("provider_message_id"), rs.getInt("attempt_count"),
				rs.getObject("available_at", OffsetDateTime.class), rs.getObject("locked_until", OffsetDateTime.class),
				rs.getObject("last_attempt_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private String maskedRecipient(String recipient) {
		if (recipient == null) return null;
		int separator = recipient.indexOf('@');
		if (separator <= 0) return recipient;
		String local = recipient.substring(0, separator);
		String visible = local.substring(0, Math.min(2, local.length()));
		return visible + "***" + recipient.substring(separator);
	}

	public record CreateNotificationData(
			String channel, String recipient, String subject, String body, String bodyHtml,
			String status, String category, String recipientType, UUID recipientId, String triggerType,
			String referenceType, UUID referenceId, OffsetDateTime scheduledAt, String idempotencyKey
	) {
	}

	public record NotificationRecord(
			UUID id, String channel, String recipient, String subject, String body, String bodyHtml,
			String status, String category, String recipientType, UUID recipientId, String triggerType,
			String referenceType, UUID referenceId, OffsetDateTime scheduledAt, OffsetDateTime sentAt,
			String errorMessage, String providerKey, String providerMessageId, int attemptCount,
			OffsetDateTime availableAt, OffsetDateTime lockedUntil, OffsetDateTime lastAttemptAt,
			OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {
	}

	public record RecipientRecord(String email, String displayName) {
	}

	public record EmailConfigurationRecord(
			UUID organizationId, boolean emailEnabled, String emailProviderKey, String fromEmail,
			String fromName, String replyTo, OffsetDateTime updatedAt
	) {
	}

	public record EmailPreferenceRecord(boolean emailEnabled, boolean marketingEnabled, OffsetDateTime updatedAt) {
	}

	public record EmailDeliveryRecord(
			UUID id, UUID organizationId, String recipient, String subject, String body, String bodyHtml,
			String category, String idempotencyKey, int attemptCount, String providerKey,
			String fromEmail, String fromName, String replyTo
	) {
	}

	public record NotificationOperationsRecord(UUID organizationId, long pending, long deadLetters, long expiredLeases) {
	}
}
