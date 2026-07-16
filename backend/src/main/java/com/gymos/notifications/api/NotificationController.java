package com.gymos.notifications.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import com.gymos.notifications.infrastructure.NotificationRepository.NotificationRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
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

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateNotificationRequest(
			@NotBlank @Size(max = 40) String channel,
			@Size(max = 180) String recipient,
			@Size(max = 180) String subject,
			@NotBlank String body,
			@Size(max = 80) String triggerType,
			@Size(max = 80) String referenceType,
			UUID referenceId,
			OffsetDateTime scheduledAt
	) {
		private CreateNotificationCommand toCommand() {
			return new CreateNotificationCommand(channel, recipient, subject, body, triggerType, referenceType, referenceId, scheduledAt);
		}
	}
}
