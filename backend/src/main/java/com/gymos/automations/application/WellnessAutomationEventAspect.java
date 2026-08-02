package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.api.operations.AutomationDomainEvents.DomainEvent;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.infrastructure.WellnessRepository.AppointmentRecord;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class WellnessAutomationEventAspect {
    private final AutomationDomainEvents domainEvents;
    private final ObjectMapper objectMapper;

    public WellnessAutomationEventAspect(
            AutomationDomainEvents domainEvents,
            ObjectMapper objectMapper
    ) {
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    @AfterReturning(
            pointcut = "execution(* com.gymos.wellness.application.WellnessService.createAppointment(..))",
            returning = "result"
    )
    public void appointmentCreated(JoinPoint joinPoint, AppointmentRecord result) {
        publish(joinPoint, result, "WELLNESS_APPOINTMENT_CREATED", "wellness-created:");
    }

    @AfterReturning(
            pointcut = "execution(* com.gymos.wellness.application.WellnessService.cancelAppointment(..))",
            returning = "result"
    )
    public void appointmentCancelled(JoinPoint joinPoint, AppointmentRecord result) {
        if (result == null || !"CANCELLED".equals(result.status())) return;
        publish(joinPoint, result, "WELLNESS_APPOINTMENT_CANCELLED", "wellness-cancelled:");
    }

    private void publish(
            JoinPoint joinPoint,
            AppointmentRecord appointment,
            String eventType,
            String prefix
    ) {
        GymOsPrincipal principal = principal(joinPoint);
        if (principal == null || appointment == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", appointment.id().toString());
        payload.put("professionalId", appointment.professionalId().toString());
        payload.put("professionalName", appointment.professionalName());
        payload.put("clientId", appointment.clientId().toString());
        payload.put("status", appointment.status());
        payload.put("startsAt", appointment.startsAt().toString());
        payload.put("endsAt", appointment.endsAt().toString());
        if (appointment.serviceId() != null) payload.put("serviceId", appointment.serviceId().toString());
        if (appointment.serviceName() != null) payload.put("serviceName", appointment.serviceName());
        try {
            domainEvents.publish(new DomainEvent(
                    principal.organizationId(), principal.branchId(), eventType,
                    prefix + appointment.id(), 1, OffsetDateTime.now(),
                    objectMapper.writeValueAsString(payload)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize wellness automation event", exception);
        }
    }

    private GymOsPrincipal principal(JoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof GymOsPrincipal principal) return principal;
        }
        return null;
    }
}
