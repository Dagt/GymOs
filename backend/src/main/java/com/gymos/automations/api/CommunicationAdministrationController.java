package com.gymos.automations.api;

import com.gymos.automations.application.CommunicationAdministrationService;
import com.gymos.automations.application.CommunicationAdministrationService.CreateTemplate;
import com.gymos.automations.application.CommunicationAdministrationService.UpdatePreference;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.DeliverySummary;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.PreferenceRecord;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.TemplateRecord;
import com.gymos.common.security.GymOsPrincipal;
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
@RequestMapping("/api/v1/communications")
public class CommunicationAdministrationController {
    private final CommunicationAdministrationService service;

    public CommunicationAdministrationController(CommunicationAdministrationService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    List<TemplateRecord> templates(@AuthenticationPrincipal GymOsPrincipal principal) {
        return service.templates(principal);
    }

    @PostMapping("/templates")
    TemplateRecord createTemplate(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @Valid @RequestBody CreateTemplateRequest request
    ) {
        return service.createTemplate(principal, request.toCommand());
    }

    @DeleteMapping("/templates/{templateId}")
    ResponseEntity<Void> archiveTemplate(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID templateId
    ) {
        service.archiveTemplate(principal, templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/clients/{clientId}/preferences")
    PreferenceRecord preference(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID clientId
    ) {
        return service.preference(principal, clientId);
    }

    @PutMapping("/clients/{clientId}/preferences")
    PreferenceRecord savePreference(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID clientId,
            @Valid @RequestBody UpdatePreferenceRequest request
    ) {
        return service.savePreference(principal, clientId, request.toCommand());
    }

    @GetMapping("/delivery-summary")
    List<DeliverySummary> deliverySummary(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days
    ) {
        return service.deliverySummary(principal, days);
    }

    public record CreateTemplateRequest(
            @NotBlank @Size(max = 120) String code,
            @NotBlank @Size(max = 32) String channel,
            @Size(max = 16) String locale,
            @Size(max = 240) String subject,
            @NotBlank @Size(max = 10000) String body,
            String allowedVariablesJson,
            @NotBlank @Size(max = 24) String status
    ) {
        CreateTemplate toCommand() {
            return new CreateTemplate(code, channel, locale, subject, body, allowedVariablesJson, status);
        }
    }

    public record UpdatePreferenceRequest(
            boolean emailOperationalEnabled,
            boolean whatsappOperationalEnabled,
            boolean emailMarketingEnabled,
            boolean whatsappMarketingEnabled,
            @Size(max = 64) String consentVersion,
            boolean consented
    ) {
        UpdatePreference toCommand() {
            return new UpdatePreference(
                    emailOperationalEnabled,
                    whatsappOperationalEnabled,
                    emailMarketingEnabled,
                    whatsappMarketingEnabled,
                    consentVersion,
                    consented
            );
        }
    }
}
