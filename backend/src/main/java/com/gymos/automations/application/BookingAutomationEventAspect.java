package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.api.operations.AutomationDomainEvents.DomainEvent;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BookingAutomationEventAspect {
    private final AutomationDomainEvents domainEvents;
    private final ObjectMapper objectMapper;

    public BookingAutomationEventAspect(
            AutomationDomainEvents domainEvents,
            ObjectMapper objectMapper
    ) {
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    @AfterReturning(
            pointcut = "execution(* com.gymos.bookings.application.BookingService.createBooking(..))",
            returning = "result"
    )
    public void bookingCreated(JoinPoint joinPoint, BookingRecord result) {
        GymOsPrincipal principal = principal(joinPoint);
        if (principal == null || result == null) return;
        publish(principal, "BOOKING_CREATED", "booking-created:" + result.id(), result, null);
    }

    @AfterReturning(
            pointcut = "execution(* com.gymos.bookings.application.BookingService.transition(..))",
            returning = "result"
    )
    public void bookingTransitioned(JoinPoint joinPoint, BookingRecord result) {
        GymOsPrincipal principal = principal(joinPoint);
        if (principal == null || result == null || !"CANCELLED".equals(result.status())) return;
        publish(principal, "BOOKING_CANCELLED", "booking-cancelled:" + result.id(), result, result.status());
    }

    private void publish(
            GymOsPrincipal principal,
            String eventType,
            String eventId,
            BookingRecord booking,
            String reason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.id().toString());
        payload.put("resourceId", booking.resourceId().toString());
        payload.put("clientId", booking.clientId().toString());
        payload.put("status", booking.status());
        payload.put("startsAt", booking.startsAt().toString());
        payload.put("endsAt", booking.endsAt().toString());
        if (reason != null) payload.put("reason", reason);
        try {
            domainEvents.publish(new DomainEvent(
                    principal.organizationId(), principal.branchId(), eventType, eventId,
                    1, OffsetDateTime.now(), objectMapper.writeValueAsString(payload)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize booking automation event", exception);
        }
    }

    private GymOsPrincipal principal(JoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof GymOsPrincipal principal) return principal;
        }
        return null;
    }
}
