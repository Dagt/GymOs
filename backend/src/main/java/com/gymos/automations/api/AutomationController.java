package com.gymos.automations.api;

import com.gymos.automations.application.AutomationService;
import com.gymos.automations.application.AutomationService.AutomationRecoveryResult;
import com.gymos.automations.application.AutomationService.AutomationOperationsView;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationDeadLetterRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRepository.SchedulerStatusRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.api.PageResponse;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations")
public class AutomationController {
	private final AutomationService automationService;

	public AutomationController(AutomationService automationService) {
		this.automationService = automationService;
	}

	@GetMapping
	List<AutomationRuleRecord> rules(@AuthenticationPrincipal GymOsPrincipal principal) {
		return automationService.rules(principal);
	}

	@PostMapping
	AutomationRuleRecord create(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateRuleRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.createRule(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/{ruleId}/enable")
	AutomationRuleRecord enable(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID ruleId, HttpServletRequest servletRequest) {
		return automationService.enable(principal, ruleId, correlationId(servletRequest));
	}

	@PostMapping("/{ruleId}/disable")
	AutomationRuleRecord disable(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID ruleId, HttpServletRequest servletRequest) {
		return automationService.disable(principal, ruleId, correlationId(servletRequest));
	}

	@PostMapping("/{ruleId}/execute")
	AutomationExecutionRecord execute(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID ruleId,
			@Valid @RequestBody(required = false) ExecuteRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.execute(principal, ruleId, request == null ? null : request.triggerReference(), correlationId(servletRequest));
	}

	@GetMapping("/{ruleId}/executions")
	PageResponse<AutomationExecutionRecord> executions(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID ruleId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size
	) {
		return automationService.executions(principal, ruleId, page, size);
	}

	@GetMapping("/dead-letters")
	PageResponse<AutomationDeadLetterRecord> deadLetters(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size
	) {
		return automationService.deadLetters(principal, page, size);
	}

	@GetMapping("/scheduler")
	SchedulerStatusRecord scheduler(@AuthenticationPrincipal GymOsPrincipal principal) {
		return automationService.schedulerStatus(principal);
	}

	@PostMapping("/scheduler/pause")
	SchedulerStatusRecord pauseScheduler(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody SchedulerControlRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.setSchedulerPaused(principal, true, request.reason(), correlationId(servletRequest));
	}

	@PostMapping("/scheduler/resume")
	SchedulerStatusRecord resumeScheduler(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestBody(required = false) SchedulerControlRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.setSchedulerPaused(
				principal, false, request == null ? null : request.reason(), correlationId(servletRequest)
		);
	}

	@GetMapping("/operations")
	AutomationOperationsView operations(@AuthenticationPrincipal GymOsPrincipal principal) {
		return automationService.operations(principal);
	}

	@PostMapping("/dead-letters/{deadLetterId}/retry")
	AutomationRecoveryResult retryDeadLetter(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deadLetterId,
			@Valid @RequestBody RecoveryRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.retryDeadLetter(
				principal, deadLetterId, request.source(), request.reason(), correlationId(servletRequest)
		);
	}

	@PostMapping("/dead-letters/{deadLetterId}/discard")
	AutomationRecoveryResult discardDeadLetter(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID deadLetterId,
			@Valid @RequestBody RecoveryRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.discardDeadLetter(
				principal, deadLetterId, request.source(), request.reason(), correlationId(servletRequest)
		);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateRuleRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 80) String triggerType,
			@Size(max = 10000) String conditionJson,
			@NotBlank @Size(max = 80) String actionType,
			@Size(max = 20000) String actionConfigJson,
			int retryLimit
	) {
		private CreateAutomationRuleCommand toCommand() {
			return new CreateAutomationRuleCommand(name, triggerType, conditionJson, actionType, actionConfigJson, retryLimit);
		}
	}

	public record ExecuteRequest(@Size(max = 200) String triggerReference) {
	}

	public record RecoveryRequest(
			@NotBlank @Size(max = 20) String source,
			@NotBlank @Size(max = 500) String reason
	) {
	}

	public record SchedulerControlRequest(@Size(max = 500) String reason) {}
}
