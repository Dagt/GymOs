package com.gymos.automations.api;

import com.gymos.automations.application.AutomationRuleTestService;
import com.gymos.automations.application.AutomationRuleTestService.AutomationEmailTestResult;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations")
public class AutomationRuleTestController {
	private final AutomationRuleTestService testService;

	public AutomationRuleTestController(AutomationRuleTestService testService) {
		this.testService = testService;
	}

	@PostMapping("/{ruleId}/test-email")
	AutomationEmailTestResult testEmail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID ruleId,
			@Valid @RequestBody TestEmailRequest request,
			HttpServletRequest servletRequest
	) {
		return testService.testEmail(
				principal, ruleId, request.clientId(), correlationId(servletRequest)
		);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record TestEmailRequest(@NotNull UUID clientId) {}
}
