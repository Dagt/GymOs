package com.gymos.notifications.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationDeliveryService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import com.gymos.notifications.application.NotificationService.EmailConfigurationView;
import com.gymos.notifications.application.NotificationService.EmailPreferenceView;
import com.gymos.notifications.application.NotificationService.UpdateEmailConfigurationCommand;
import com.gymos.notifications.infrastructure.NotificationRepository.NotificationRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService notificationService;
	private final NotificationDeliveryService notificationDeliveryService;

	public NotificationController(
			NotificationService notificationService,
			NotificationDeliveryService notificationDeliveryService
	) {
		this.notificationService = notificationService;
		this.notificationDeliveryService = notificationDeliveryService;
	}

	@GetMapping
	List<NotificationRecord> notifications(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "80") int limit
	) {
		return notificationService.notifications(principal, limit);
	}

	@PostMapping
	NotificationRecord create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateNotificationRequest request,
			HttpServletRequest servletRequest
	) {
		return notificationService.create(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/email-configuration")
	EmailConfigurationView emailConfiguration(@AuthenticationPrincipal GymOsPrincipal principal) {
		return notificationService.emailConfiguration(principal);
	}

	@PutMapping("/email-configuration")
	EmailConfigurationView updateEmailConfiguration(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody UpdateEmailConfigurationRequest request,
			HttpServletRequest servletRequest
	) {
		return notificationService.updateEmailConfiguration(
				principal, request.toCommand(), correlationId(servletRequest)
		);
	}

	@PostMapping("/email-configuration/test")
	NotificationRecord testEmail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody TestEmailRequest request,
			HttpServletRequest servletRequest
	) {
		NotificationRecord queued = notificationService.queueTestEmail(
				principal, request.recipient(), correlationId(servletRequest)
		);
		notificationDeliveryService.deliverNow(principal.organizationId(), queued.id());
		return notificationService.notification(principal, queued.id());
	}

	@PutMapping("/email-preferences")
	void updateEmailPreference(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody UpdateEmailPreferenceRequest request,
			HttpServletRequest servletRequest
	) {
		notificationService.updateEmailPreference(
				principal, request.recipient(), request.emailEnabled(), request.marketingEnabled(),
				correlationId(servletRequest)
		);
	}

	@GetMapping("/email-preferences")
	EmailPreferenceView emailPreference(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam @Email @Size(max = 180) String recipient
	) {
		return notificationService.emailPreference(principal, recipient);
	}

	@PostMapping("/{notificationId}/retry")
	NotificationRecord retryEmail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID notificationId,
			HttpServletRequest servletRequest
	) {
		return notificationService.retryEmail(principal, notificationId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateNotificationRequest(
			@NotBlank @Size(max = 40) String channel,
			@Email @Size(max = 180) String recipient,
			@Size(max = 32) String recipientType,
			UUID recipientId,
			@Size(max = 180) String subject,
			@NotBlank String body,
			@Size(max = 32) String category,
			@Size(max = 80) String triggerType,
			@Size(max = 80) String referenceType,
			UUID referenceId,
			OffsetDateTime scheduledAt
	) {
		private CreateNotificationCommand toCommand() {
			return new CreateNotificationCommand(
					channel, recipient, recipientType, recipientId, subject, body, category,
					triggerType, referenceType, referenceId, scheduledAt
			);
		}
	}

	public record UpdateEmailConfigurationRequest(
			boolean enabled,
			@NotBlank @Size(max = 40) String providerKey,
			@Email @Size(max = 180) String fromEmail,
			@Size(max = 160) String fromName,
			@Email @Size(max = 180) String replyTo
	) {
		private UpdateEmailConfigurationCommand toCommand() {
			return new UpdateEmailConfigurationCommand(enabled, providerKey, fromEmail, fromName, replyTo);
		}
	}

	public record TestEmailRequest(@NotBlank @Email @Size(max = 180) String recipient) {
	}

	public record UpdateEmailPreferenceRequest(
			@NotBlank @Email @Size(max = 180) String recipient,
			boolean emailEnabled,
			boolean marketingEnabled
	) {
	}
}
