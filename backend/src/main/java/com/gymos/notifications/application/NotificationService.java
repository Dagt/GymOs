package com.gymos.notifications.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.infrastructure.NotificationRepository;
import com.gymos.notifications.infrastructure.NotificationRepository.CreateNotificationData;
import com.gymos.notifications.infrastructure.NotificationRepository.NotificationRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
	private final NotificationRepository notificationRepository;
	private final AuditService auditService;

	public NotificationService(NotificationRepository notificationRepository, AuditService auditService) {
		this.notificationRepository = notificationRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<NotificationRecord> notifications(GymOsPrincipal principal, int limit) {
		requireBranch(principal);
		return notificationRepository.notifications(principal.organizationId(), principal.branchId(), Math.max(1, Math.min(limit, 200)));
	}

	@Transactional
	public NotificationRecord create(GymOsPrincipal principal, CreateNotificationCommand command, String correlationId) {
		requireBranch(principal);
		UUID notificationId = createSystem(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				command,
				correlationId
		);
		return notificationRepository.notification(principal.organizationId(), notificationId);
	}

	@Transactional
	public UUID createSystem(UUID organizationId, UUID branchId, UUID actorUserId, CreateNotificationCommand command, String correlationId) {
		String channel = command.channel() == null || command.channel().isBlank() ? "IN_APP" : command.channel().trim().toUpperCase();
		String body = required(command.body(), "NOTIFICATION_BODY_REQUIRED", "La notificacion necesita mensaje.");
		UUID notificationId = notificationRepository.create(
				organizationId,
				branchId,
				actorUserId,
				new CreateNotificationData(
						channel,
						blankToNull(command.recipient()),
						blankToNull(command.subject()),
						body,
						blankToNull(command.triggerType()),
						blankToNull(command.referenceType()),
						command.referenceId(),
						command.scheduledAt()
				)
		);
		auditService.recordSystem(organizationId, branchId, actorUserId, "NOTIFICATION_QUEUED", "NOTIFICATION", notificationId, "AUTOMATION", null, correlationId);
		return notificationId;
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(code, message);
		}
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	public record CreateNotificationCommand(String channel, String recipient, String subject, String body, String triggerType, String referenceType, UUID referenceId, OffsetDateTime scheduledAt) {
	}
}
