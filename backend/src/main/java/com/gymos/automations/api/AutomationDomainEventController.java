package com.gymos.automations.api;

import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.api.operations.AutomationDomainEvents.DomainEvent;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations/domain-events")
public class AutomationDomainEventController {
    private final AutomationDomainEvents domainEvents;

    public AutomationDomainEventController(AutomationDomainEvents domainEvents) {
        this.domainEvents = domainEvents;
    }

    @PostMapping
    EventAccepted publish(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @Valid @RequestBody PublishEventRequest request
    ) {
        requirePermission(principal);
        UUID id = domainEvents.publish(new DomainEvent(
                principal.organizationId(),
                principal.branchId(),
                request.eventType(),
                request.eventId(),
                request.eventVersion(),
                request.occurredAt(),
                request.payloadJson()
        ));
        return new EventAccepted(id);
    }

    private void requirePermission(GymOsPrincipal principal) {
        if (principal == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        }
        if (principal.branchId() == null) {
            throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
        }
        if (!principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para publicar eventos de automatizacion.");
        }
    }

    public record PublishEventRequest(
            @NotBlank @Size(max = 80) String eventType,
            @NotBlank @Size(max = 240) String eventId,
            @Min(1) int eventVersion,
            OffsetDateTime occurredAt,
            @NotBlank String payloadJson
    ) {}

    public record EventAccepted(UUID id) {}
}
