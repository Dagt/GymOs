package com.gymos.automations.api;

import com.gymos.automations.application.AutomationRuleManagementService;
import com.gymos.automations.application.AutomationRuleManagementService.PreviewResult;
import com.gymos.automations.application.AutomationRuleManagementService.UpdateDefinition;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRuleManagementRepository.ArchivedRule;
import com.gymos.automations.infrastructure.AutomationRuleManagementRepository.RuleRevision;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations")
public class AutomationRuleManagementController {
    private final AutomationRuleManagementService service;

    public AutomationRuleManagementController(AutomationRuleManagementService service) {
        this.service = service;
    }

    @PutMapping("/{ruleId}")
    AutomationRuleRecord update(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateRuleRequest request,
            HttpServletRequest servletRequest
    ) {
        return service.update(principal, ruleId, request.toCommand(), correlationId(servletRequest));
    }

    @PostMapping("/{ruleId}/duplicate")
    AutomationRuleRecord duplicate(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId,
            HttpServletRequest servletRequest
    ) {
        return service.duplicate(principal, ruleId, correlationId(servletRequest));
    }

    @DeleteMapping("/{ruleId}")
    ResponseEntity<Void> archive(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId,
            HttpServletRequest servletRequest
    ) {
        service.archive(principal, ruleId, correlationId(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ruleId}/restore")
    ArchivedRule restore(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId,
            HttpServletRequest servletRequest
    ) {
        return service.restore(principal, ruleId, correlationId(servletRequest));
    }

    @GetMapping("/{ruleId}/versions")
    List<RuleRevision> versions(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId
    ) {
        return service.revisions(principal, ruleId);
    }

    @GetMapping("/{ruleId}/preview")
    PreviewResult preview(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID ruleId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit
    ) {
        return service.preview(principal, ruleId, limit);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    public record UpdateRuleRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String triggerType,
            String conditionJson,
            @NotBlank @Size(max = 80) String actionType,
            String actionConfigJson,
            @Min(0) @Max(10) int retryLimit
    ) {
        UpdateDefinition toCommand() {
            return new UpdateDefinition(name, triggerType, conditionJson, actionType, actionConfigJson, retryLimit);
        }
    }
}
