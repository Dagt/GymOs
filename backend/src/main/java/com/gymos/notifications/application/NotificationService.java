package com.gymos.notifications.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.infrastructure.NotificationRepository;
import com.gymos.notifications.infrastructure.NotificationRepository.CreateNotificationData;
import com.gymos.notifications.infrastructure.NotificationRepository.EmailConfigurationRecord;
import com.gymos.notifications.infrastructure.NotificationRepository.NotificationRecord;
import com.gymos.notifications.infrastructure.NotificationRepository.RecipientRecord;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
	private static final java.util.Set<String> SUPPORTED_CHANNELS = java.util.Set.of("IN_APP", "EMAIL");
	private static final java.util.Set<String> CATEGORIES = java.util.Set.of("TRANSACTIONAL", "MARKETING");
	private static final java.util.Set<String> RECIPIENT_TYPES = java.util.Set.of("CLIENT", "USER", "DIRECT");

	private final NotificationRepository notificationRepository;
	private final AuditService auditService;
	private final Map<String, EmailDeliveryPort> emailProviders;
	private final NotificationRecipientHasher recipientHasher;
	private final NotificationProperties.Smtp smtpProperties;

	public NotificationService(
			NotificationRepository notificationRepository,
			AuditService auditService,
			List<EmailDeliveryPort> emailProviders,
			NotificationRecipientHasher recipientHasher,
			NotificationProperties notificationProperties
	) {
		this.notificationRepository = notificationRepository;
		this.auditService = auditService;
		this.emailProviders = emailProviders.stream().collect(Collectors.toUnmodifiableMap(
				provider -> provider.providerKey().toUpperCase(Locale.ROOT), Function.identity()
		));
		this.recipientHasher = recipientHasher;
		this.smtpProperties = notificationProperties.smtp();
	}

	@Transactional(readOnly = true)
	public List<NotificationRecord> notifications(GymOsPrincipal principal, int limit) {
		requireBranch(principal);
		return notificationRepository.notifications(
				principal.organizationId(), principal.branchId(), Math.max(1, Math.min(limit, 200))
		);
	}

	@Transactional
	public NotificationRecord create(GymOsPrincipal principal, CreateNotificationCommand command, String correlationId) {
		requireBranch(principal);
		UUID notificationId = createSystem(
				principal.organizationId(), principal.branchId(), principal.userId(), command, correlationId
		);
		return notificationRepository.notification(principal.organizationId(), notificationId);
	}

	@Transactional
	public UUID createSystem(
			UUID organizationId, UUID branchId, UUID actorUserId,
			CreateNotificationCommand command, String correlationId
	) {
		return createSystemIdempotent(organizationId, branchId, actorUserId, command, correlationId, null);
	}

	@Transactional
	public UUID createSystemIdempotent(
			UUID organizationId, UUID branchId, UUID actorUserId, CreateNotificationCommand command,
			String correlationId, String idempotencyKey
	) {
		String channel = normalize(command.channel(), "IN_APP");
		if (!SUPPORTED_CHANNELS.contains(channel)) {
			throw new BusinessException(
					"NOTIFICATION_CHANNEL_UNSUPPORTED",
					"El canal aun no tiene un adaptador de entrega. Usa IN_APP o EMAIL."
			);
		}
		String category = normalize(command.category(), "TRANSACTIONAL");
		if (!CATEGORIES.contains(category)) {
			throw new BusinessException("NOTIFICATION_CATEGORY_INVALID", "La categoria de la notificacion no es valida.");
		}
		String body = limitedRequired(command.body(), 5000, "NOTIFICATION_BODY_REQUIRED", "La notificacion necesita mensaje.");
		String subject = limited(command.subject(), 180, "NOTIFICATION_SUBJECT_INVALID");
		String recipientType = normalizeRecipientType(command.recipientType(), command.recipientId(), command.recipient());
		ResolvedRecipient resolvedRecipient = resolveRecipient(
				organizationId, channel, recipientType, command.recipientId(), command.recipient()
		);
		String recipient = resolvedRecipient.email();
		body = renderRecipient(body, resolvedRecipient.displayName());
		subject = renderRecipient(subject, resolvedRecipient.displayName());
		String status = "QUEUED";
		if ("EMAIL".equals(channel)) {
			if (subject == null) {
				throw new BusinessException("NOTIFICATION_EMAIL_SUBJECT_REQUIRED", "El correo necesita asunto.");
			}
			EmailConfigurationView configuration = emailConfiguration(organizationId);
			if (!configuration.emailEnabled()) {
				throw new BusinessException("NOTIFICATION_EMAIL_DISABLED", "El gimnasio no tiene habilitado el envio de correo.");
			}
			if (!configuration.ready()) {
				throw new BusinessException("NOTIFICATION_EMAIL_PROVIDER_UNAVAILABLE", "El proveedor de correo no esta conectado.");
			}
			if (!notificationRepository.emailAllowed(organizationId, recipientHasher.hash(recipient), category)) {
				status = "SUPPRESSED";
			}
		}
		UUID notificationId = notificationRepository.create(
				organizationId, branchId, actorUserId,
				new CreateNotificationData(
						channel, recipient, subject, body, null, status, category, recipientType,
						command.recipientId(), blankToNull(command.triggerType()),
						blankToNull(command.referenceType()), command.referenceId(), command.scheduledAt(),
						limited(idempotencyKey, 200, "NOTIFICATION_IDEMPOTENCY_KEY_INVALID")
				)
		);
		auditService.recordSystem(
				organizationId, branchId, actorUserId,
				"SUPPRESSED".equals(status) ? "NOTIFICATION_SUPPRESSED" : "NOTIFICATION_QUEUED",
				"NOTIFICATION", notificationId, "AUTOMATION", channel, correlationId
		);
		return notificationId;
	}

	@Transactional(readOnly = true)
	public EmailConfigurationView emailConfiguration(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return emailConfiguration(principal.organizationId());
	}

	@Transactional
	public EmailConfigurationView updateEmailConfiguration(
			GymOsPrincipal principal, UpdateEmailConfigurationCommand command, String correlationId
	) {
		requirePrincipal(principal);
		String providerKey = normalize(command.providerKey(), "SMTP");
		String fromEmail = normalizeEmail(command.fromEmail(), command.enabled());
		String replyTo = normalizeEmail(command.replyTo(), false);
		String fromName = limited(command.fromName(), 160, "NOTIFICATION_FROM_NAME_INVALID");
		EmailDeliveryPort provider = emailProviders.get(providerKey);
		if (command.enabled() && (provider == null || !provider.available())) {
			throw new BusinessException(
					"NOTIFICATION_EMAIL_PROVIDER_UNAVAILABLE",
					"Configura y conecta el proveedor " + providerKey + " antes de habilitar el correo."
			);
		}
		if (command.enabled() && "SMTP".equals(providerKey) && !smtpProperties.allowsFromAddress(fromEmail)) {
			throw new BusinessException(
					"NOTIFICATION_FROM_EMAIL_NOT_AUTHORIZED",
					"El correo remitente no esta autorizado en " + smtpProperties.displayName()
							+ ". Usa un remitente verificado y asociado al Agent configurado."
			);
		}
		EmailConfigurationRecord updated = notificationRepository.upsertEmailConfiguration(
				principal.organizationId(), principal.userId(), command.enabled(), providerKey,
				fromEmail, fromName, replyTo
		);
		auditService.record(
				principal, "NOTIFICATION_EMAIL_CONFIGURATION_UPDATED", "ORGANIZATION",
				principal.organizationId(), "SETTINGS", command.enabled() ? "ENABLED" : "DISABLED", correlationId
		);
		return view(updated);
	}

	@Transactional
	public NotificationRecord queueTestEmail(
			GymOsPrincipal principal, String recipient, String correlationId
	) {
		return create(
				principal,
				new CreateNotificationCommand(
						"EMAIL", recipient, "DIRECT", null, "Prueba de correo de GymFort",
						"Esta es una prueba real de entrega. Si recibiste este mensaje, el canal de correo de tu gimnasio esta listo para las automatizaciones.", "TRANSACTIONAL",
						"MANUAL_TEST", "ORGANIZATION", principal.organizationId(), null
				),
				correlationId
		);
	}

	@Transactional(readOnly = true)
	public NotificationRecord notification(GymOsPrincipal principal, UUID notificationId) {
		requirePrincipal(principal);
		return notificationRepository.notification(principal.organizationId(), notificationId);
	}

	@Transactional
	public void updateEmailPreference(
			GymOsPrincipal principal, String recipient, boolean emailEnabled,
			boolean marketingEnabled, String correlationId
	) {
		requirePreferenceManagement(principal);
		String normalized = normalizeEmail(recipient, true);
		notificationRepository.setEmailPreference(
				principal.organizationId(), recipientHasher.hash(normalized), emailEnabled,
				emailEnabled && marketingEnabled, principal.userId()
		);
		auditService.record(
				principal, "NOTIFICATION_EMAIL_PREFERENCE_UPDATED", "ORGANIZATION",
				principal.organizationId(), "SETTINGS", null, correlationId
		);
	}

	@Transactional(readOnly = true)
	public EmailPreferenceView emailPreference(GymOsPrincipal principal, String recipient) {
		requirePreferenceManagement(principal);
		String normalized = normalizeEmail(recipient, true);
		var preference = notificationRepository.emailPreference(
				principal.organizationId(), recipientHasher.hash(normalized)
		);
		return new EmailPreferenceView(
				preference.emailEnabled(), preference.marketingEnabled(), preference.updatedAt()
		);
	}

	@Transactional
	public NotificationRecord retryEmail(
			GymOsPrincipal principal, UUID notificationId, String correlationId
	) {
		requireBranch(principal);
		if (!notificationRepository.requeueDeadLetter(principal.organizationId(), notificationId, OffsetDateTime.now())) {
			throw new BusinessException("NOTIFICATION_NOT_RETRYABLE", "El correo no existe o no esta en dead letter.");
		}
		auditService.record(
				principal, "NOTIFICATION_EMAIL_RETRIED", "NOTIFICATION", notificationId,
				"AUTOMATIONS", null, correlationId
		);
		return notificationRepository.notification(principal.organizationId(), notificationId);
	}

	private EmailConfigurationView emailConfiguration(UUID organizationId) {
		return view(notificationRepository.emailConfiguration(organizationId));
	}

	private EmailConfigurationView view(EmailConfigurationRecord record) {
		EmailDeliveryPort provider = emailProviders.get(record.emailProviderKey().toUpperCase(Locale.ROOT));
		boolean available = provider != null && provider.available();
		String preferredFromEmail = "SMTP".equalsIgnoreCase(record.emailProviderKey())
				? smtpProperties.preferredFromAddress() : null;
		String fromEmail = record.fromEmail() == null ? preferredFromEmail : record.fromEmail();
		boolean ready = record.emailEnabled() && available && fromEmail != null
				&& (!"SMTP".equalsIgnoreCase(record.emailProviderKey()) || smtpProperties.allowsFromAddress(fromEmail));
		return new EmailConfigurationView(
				record.organizationId(), record.emailEnabled(), record.emailProviderKey(),
				fromEmail, record.fromName(), record.replyTo(), available, ready,
				smtpProperties.displayName(), preferredFromEmail, smtpProperties.restrictsFromAddresses(),
				smtpProperties.allowedFromAddresses(),
				notificationRepository.lastSuccessfulEmailTestAt(record.organizationId()), record.updatedAt()
		);
	}

	private ResolvedRecipient resolveRecipient(
			UUID organizationId, String channel, String recipientType, UUID recipientId, String directRecipient
	) {
		if (!"EMAIL".equals(channel)) return new ResolvedRecipient(blankToNull(directRecipient), null);
		String resolved = blankToNull(directRecipient);
		String displayName = null;
		if (resolved == null && recipientId != null && !"DIRECT".equals(recipientType)) {
			RecipientRecord recipient = notificationRepository.recipient(organizationId, recipientType, recipientId)
					.orElse(null);
			if (recipient != null) {
				resolved = recipient.email();
				displayName = recipient.displayName();
			}
		}
		if (resolved == null) {
			throw new BusinessException("NOTIFICATION_EMAIL_RECIPIENT_REQUIRED", "El destinatario no tiene correo registrado.");
		}
		return new ResolvedRecipient(normalizeEmail(resolved, true), blankToNull(displayName));
	}

	private String renderRecipient(String template, String displayName) {
		if (template == null) return null;
		return template.replace("{{recipientName}}", displayName == null ? "socio" : displayName);
	}

	private String normalizeRecipientType(String value, UUID recipientId, String recipient) {
		String normalized = normalize(value, recipientId == null ? "DIRECT" : "CLIENT");
		if (!RECIPIENT_TYPES.contains(normalized)) {
			throw new BusinessException("NOTIFICATION_RECIPIENT_TYPE_INVALID", "El tipo de destinatario no es valido.");
		}
		if ("DIRECT".equals(normalized) && recipient == null && recipientId != null) {
			throw new BusinessException("NOTIFICATION_RECIPIENT_INVALID", "El destinatario directo necesita una direccion.");
		}
		return normalized;
	}

	private String normalizeEmail(String value, boolean required) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			if (required) throw new BusinessException("NOTIFICATION_EMAIL_REQUIRED", "Indica una direccion de correo.");
			return null;
		}
		if (normalized.length() > 180 || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
			throw new BusinessException("NOTIFICATION_EMAIL_INVALID", "La direccion de correo no es valida.");
		}
		try {
			InternetAddress address = new InternetAddress(normalized, true);
			address.validate();
		} catch (AddressException exception) {
			throw new BusinessException("NOTIFICATION_EMAIL_INVALID", "La direccion de correo no es valida.");
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String limitedRequired(String value, int max, String code, String message) {
		String normalized = blankToNull(value);
		if (normalized == null) throw new BusinessException(code, message);
		if (normalized.length() > max) throw new BusinessException(code, "El valor es demasiado largo.");
		return normalized;
	}

	private String limited(String value, int max, String code) {
		String normalized = blankToNull(value);
		if (normalized != null && normalized.length() > max) {
			throw new BusinessException(code, "El valor es demasiado largo.");
		}
		return normalized;
	}

	private String normalize(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
	}

	private void requireBranch(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	private void requirePreferenceManagement(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (!principal.permissions().contains("client.update")
				&& !principal.permissions().contains("notification.manage")
				&& !principal.permissions().contains("notification.settings.manage")
				&& !principal.permissions().contains("platform.admin")) {
			throw new BusinessException(
					"NOTIFICATION_PREFERENCE_FORBIDDEN",
					"No tienes permiso para administrar las preferencias de correo."
			);
		}
	}

	public record CreateNotificationCommand(
			String channel,
			String recipient,
			String recipientType,
			UUID recipientId,
			String subject,
			String body,
			String category,
			String triggerType,
			String referenceType,
			UUID referenceId,
			OffsetDateTime scheduledAt
	) {
		public CreateNotificationCommand(
				String channel, String recipient, String subject, String body, String triggerType,
				String referenceType, UUID referenceId, OffsetDateTime scheduledAt
		) {
			this(channel, recipient, recipient == null ? null : "DIRECT", null, subject, body,
					"TRANSACTIONAL", triggerType, referenceType, referenceId, scheduledAt);
		}
	}

	public record UpdateEmailConfigurationCommand(
			boolean enabled, String providerKey, String fromEmail, String fromName, String replyTo
	) {
	}

	public record EmailConfigurationView(
			UUID organizationId, boolean emailEnabled, String providerKey, String fromEmail,
			String fromName, String replyTo, boolean providerAvailable, boolean ready,
			String providerName, String preferredFromEmail, boolean fromEmailRestricted,
			List<String> allowedFromEmails,
			OffsetDateTime lastVerifiedAt,
			OffsetDateTime updatedAt
	) {
	}

	public record EmailPreferenceView(boolean emailEnabled, boolean marketingEnabled, OffsetDateTime updatedAt) {
	}

	private record ResolvedRecipient(String email, String displayName) {
	}
}
