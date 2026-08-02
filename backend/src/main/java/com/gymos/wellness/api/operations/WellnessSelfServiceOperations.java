package com.gymos.wellness.api.operations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WellnessSelfServiceOperations {
    List<Professional> professionals(UUID organizationId, UUID branchId);

    List<Service> services(UUID organizationId, UUID branchId, UUID professionalId);

    List<Availability> availability(
            UUID organizationId,
            UUID branchId,
            UUID professionalId,
            UUID serviceId,
            LocalDate date
    );

    Appointment createFreeAppointment(
            UUID organizationId,
            UUID branchId,
            UUID clientId,
            UUID professionalId,
            UUID serviceId,
            OffsetDateTime startsAt,
            String notes,
            String correlationId
    );

    record Professional(
            UUID id,
            String displayName,
            String professionalType,
            String bio,
            String officeLocation,
            String engagementType
    ) {}

    record Service(
            UUID id,
            UUID professionalId,
            String name,
            String serviceType,
            int durationMinutes,
            long priceCents,
            String currency
    ) {}

    record Availability(OffsetDateTime startsAt, OffsetDateTime endsAt, boolean available) {}

    record Appointment(
            UUID id,
            UUID professionalId,
            String professionalName,
            UUID serviceId,
            String serviceName,
            long priceCents,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status
    ) {}
}
