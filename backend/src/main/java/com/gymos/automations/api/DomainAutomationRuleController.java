package com.gymos.automations.api;

import com.gymos.automations.application.DomainAutomationRuleService;
import com.gymos.automations.application.DomainAutomationRuleService.CreateDomainRule;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations/domain-rules")
public class DomainAutomationRuleController {
    private final DomainAutomationRuleService service;

    public DomainAutomationRuleController(DomainAutomationRuleService service) {
        this.service = service;
    }

    @PostMapping
    AutomationRuleRecord create(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @Valid @RequestBody CreateDomainRuleRequest request,
            HttpServletRequest servletRequest
    ) {
        return service.create(principal, request.toCommand(), correlationId(servletRequest));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    public record CreateDomainRuleRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String triggerType,
            String conditionJson,
            String actionConfigJson,
            @Min(0) @Max(10) int retryLimit
    ) {
        CreateDomainRule toCommand() {
            return new CreateDomainRule(name, triggerType, conditionJson, actionConfigJson, retryLimit);
        }
    }
}
