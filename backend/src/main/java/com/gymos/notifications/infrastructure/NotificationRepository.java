package com.gymos.notifications.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
	private final JdbcClient jdbcClient;

	public NotificationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<NotificationRecord> notifications(UUID organizationId, UUID branchId, int limit) {
		return jdbcClient.sql("""
				SELECT id, channel, recipient, subject, body, status, trigger_type,
				       reference_type, reference_id, scheduled_at, sent_at, error_message, created_at
				FROM notification_log
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				ORDER BY created_at DESC
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("limit", limit)
				.query(this::mapNotification)
				.list();
	}

	public UUID create(UUID organizationId, UUID branchId, UUID actorUserId, CreateNotificationData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID notificationId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO notification_log (
				    id, organization_id, branch_id, channel, recipient, subject, body,
				    status, trigger_type, reference_type, reference_id, scheduled_at,
				    created_at, created_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :channel, :recipient, :subject, :body,
				    'QUEUED', :triggerType, :referenceType, :referenceId, :scheduledAt,
				    :createdAt, :createdBy, 0
				)
				""")
				.param("id", notificationId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("channel", data.channel())
				.param("recipient", data.recipient())
				.param("subject", data.subject())
				.param("body", data.body())
				.param("triggerType", data.triggerType())
				.param("referenceType", data.referenceType())
				.param("referenceId", data.referenceId())
				.param("scheduledAt", data.scheduledAt())
				.param("createdAt", now)
				.param("createdBy", actorUserId)
				.update();
		return notificationId;
	}

	public NotificationRecord notification(UUID organizationId, UUID notificationId) {
		return jdbcClient.sql("""
				SELECT id, channel, recipient, subject, body, status, trigger_type,
				       reference_type, reference_id, scheduled_at, sent_at, error_message, created_at
				FROM notification_log
				WHERE organization_id = :organizationId
				  AND id = :notificationId
				""")
				.param("organizationId", organizationId)
				.param("notificationId", notificationId)
				.query(this::mapNotification)
				.single();
	}

	private NotificationRecord mapNotification(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new NotificationRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("channel"),
				resultSet.getString("recipient"),
				resultSet.getString("subject"),
				resultSet.getString("body"),
				resultSet.getString("status"),
				resultSet.getString("trigger_type"),
				resultSet.getString("reference_type"),
				resultSet.getObject("reference_id", UUID.class),
				resultSet.getObject("scheduled_at", OffsetDateTime.class),
				resultSet.getObject("sent_at", OffsetDateTime.class),
				resultSet.getString("error_message"),
				resultSet.getObject("created_at", OffsetDateTime.class)
		);
	}

	public record CreateNotificationData(String channel, String recipient, String subject, String body, String triggerType, String referenceType, UUID referenceId, OffsetDateTime scheduledAt) {
	}

	public record NotificationRecord(UUID id, String channel, String recipient, String subject, String body, String status, String triggerType, String referenceType, UUID referenceId, OffsetDateTime scheduledAt, OffsetDateTime sentAt, String errorMessage, OffsetDateTime createdAt) {
	}
}
