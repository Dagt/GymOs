package com.gymos.automations.api;

import com.gymos.automations.application.AutomationService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.DurableAutomationRepository.WorkItemRecord;
import com.gymos.common.api.Correlation;
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
			@RequestBody(required = false) ExecuteRequest request,
			HttpServletRequest servletRequest
	) {
		return automationService.execute(principal, ruleId, request == null ? null : request.triggerReference(), correlationId(servletRequest));
	}

	@GetMapping("/{ruleId}/executions")
	List<AutomationExecutionRecord> executions(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID ruleId) {
		return automationService.executions(principal, ruleId);
	}

	@GetMapping("/work-items")
	List<WorkItemRecord> workItems(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) UUID ruleId,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "100") int limit
	) {
		return automationService.workItems(principal, ruleId, status, limit);
	}

	@GetMapping("/work-items/{workItemId}")
	WorkItemRecord workItem(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID workItemId) {
		return automationService.workItem(principal, workItemId);
	}

	@PostMapping("/work-items/{workItemId}/retry")
	WorkItemRecord retryWorkItem(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workItemId,
			HttpServletRequest servletRequest
	) {
		return automationService.retryWorkItem(principal, workItemId, correlationId(servletRequest));
	}

	@PostMapping("/work-items/{workItemId}/cancel")
	WorkItemRecord cancelWorkItem(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workItemId,
			HttpServletRequest servletRequest
	) {
		return automationService.cancelWorkItem(principal, workItemId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateRuleRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 80) String triggerType,
			String conditionJson,
			@NotBlank @Size(max = 80) String actionType,
			String actionConfigJson,
			int retryLimit
	) {
		private CreateAutomationRuleCommand toCommand() {
			return new CreateAutomationRuleCommand(name, triggerType, conditionJson, actionType, actionConfigJson, retryLimit);
		}
	}

	public record ExecuteRequest(String triggerReference) {
	}
}
