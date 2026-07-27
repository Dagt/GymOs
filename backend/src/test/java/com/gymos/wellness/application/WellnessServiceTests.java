package com.gymos.wellness.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.application.command.CreateProfessionalAvailabilityCommand;
import com.gymos.wellness.application.command.CreateProfessionalCommand;
import com.gymos.wellness.application.command.CreateProfessionalServiceCommand;
import com.gymos.wellness.application.command.CreateWellnessAppointmentCommand;
import com.gymos.wellness.application.command.UpdateProfessionalCommand;
import com.gymos.wellness.application.command.UpdateProfessionalServiceCommand;
import com.gymos.wellness.infrastructure.WellnessRepository;
import com.gymos.wellness.infrastructure.WellnessRepository.AppointmentRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.AvailabilityRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WellnessServiceTests {
	@Mock
	private WellnessRepository wellnessRepository;
	@Mock
	private AuditService auditService;

	private WellnessService wellnessService;

	private final UUID orgId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(userId, orgId, branchId, "test@gymos.com", "Test User", Set.of("professional.read", "professional.manage", "booking.read", "booking.manage"));

	@BeforeEach
	void setUp() {
		wellnessService = new WellnessService(wellnessRepository, auditService);
	}

	@Test
	void createProfessional_createsRecordWithOfficeLocation() {
		UUID professionalId = UUID.randomUUID();
		when(wellnessRepository.createProfessional(eq(orgId), eq(branchId), eq(userId), any())).thenReturn(professionalId);

		ProfessionalRecord record = new ProfessionalRecord(professionalId, "Dra. Ana Lopez", "NUTRITIONIST", "ana@test.com", "5551234", "Bio", "Consultorio 302", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
		when(wellnessRepository.professional(orgId, branchId, professionalId)).thenReturn(Optional.of(record));

		CreateProfessionalCommand command = new CreateProfessionalCommand("Dra. Ana Lopez", "NUTRITIONIST", "ana@test.com", "5551234", "Bio", "Consultorio 302");
		ProfessionalRecord result = wellnessService.createProfessional(principal, command, "corr-1");

		assertThat(result.displayName()).isEqualTo("Dra. Ana Lopez");
		assertThat(result.officeLocation()).isEqualTo("Consultorio 302");
		verify(auditService).record(principal, "PROFESSIONAL_CREATED", "PROFESSIONAL", professionalId, "BACKOFFICE", null, "corr-1");
	}

	@Test
	void createAppointment_createsRequestedAppointment() {
		UUID professionalId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID appointmentId = UUID.randomUUID();

		ProfessionalRecord profRecord = new ProfessionalRecord(professionalId, "Dra. Ana", "NUTRITIONIST", null, null, null, "Consultorio 1", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
		when(wellnessRepository.professional(orgId, branchId, professionalId)).thenReturn(Optional.of(profRecord));

		ProfessionalServiceRecord serviceRecord = new ProfessionalServiceRecord(serviceId, professionalId, "Consulta Nutricional", "NUTRITION", 60, 50000, "MXN", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
		when(wellnessRepository.service(orgId, branchId, professionalId, serviceId)).thenReturn(Optional.of(serviceRecord));

		OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);
		when(wellnessRepository.overlappingAppointmentsCount(eq(orgId), eq(branchId), eq(professionalId), eq(startsAt), any())).thenReturn(0);

		when(wellnessRepository.createAppointment(eq(orgId), eq(branchId), eq(userId), any())).thenReturn(appointmentId);

		AppointmentRecord appointmentRecord = new AppointmentRecord(
				appointmentId, orgId, branchId, professionalId, "Dra. Ana", "NUTRITIONIST", "Consultorio 1",
				serviceId, "Consulta Nutricional", 60, 50000, clientId, "Juan Perez", "juan@test.com", "5550000",
				startsAt, startsAt.plusMinutes(60), "REQUESTED", "Primera cita", null, null, null,
				OffsetDateTime.now(), OffsetDateTime.now()
		);
		when(wellnessRepository.appointment(orgId, branchId, appointmentId)).thenReturn(Optional.of(appointmentRecord));

		CreateWellnessAppointmentCommand command = new CreateWellnessAppointmentCommand(professionalId, serviceId, clientId, startsAt, "Primera cita");
		AppointmentRecord result = wellnessService.createAppointment(principal, command, "corr-app");

		assertThat(result.id()).isEqualTo(appointmentId);
		assertThat(result.status()).isEqualTo("REQUESTED");
		verify(auditService).record(principal, "WELLNESS_APPOINTMENT_CREATED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", null, "corr-app");
	}

	@Test
	void createAppointment_throwsIfSlotOverlaps() {
		UUID professionalId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();

		ProfessionalRecord profRecord = new ProfessionalRecord(professionalId, "Dra. Ana", "NUTRITIONIST", null, null, null, "Consultorio 1", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
		when(wellnessRepository.professional(orgId, branchId, professionalId)).thenReturn(Optional.of(profRecord));

		ProfessionalServiceRecord serviceRecord = new ProfessionalServiceRecord(serviceId, professionalId, "Consulta Nutricional", "NUTRITION", 60, 50000, "MXN", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
		when(wellnessRepository.service(orgId, branchId, professionalId, serviceId)).thenReturn(Optional.of(serviceRecord));

		OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);
		when(wellnessRepository.overlappingAppointmentsCount(eq(orgId), eq(branchId), eq(professionalId), eq(startsAt), any())).thenReturn(1);

		CreateWellnessAppointmentCommand command = new CreateWellnessAppointmentCommand(professionalId, serviceId, clientId, startsAt, null);

		assertThatThrownBy(() -> wellnessService.createAppointment(principal, command, "corr-app"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("El profesional ya tiene una cita agendada");
	}

	@Test
	void confirmAppointment_updatesStatusToConfirmed() {
		UUID appointmentId = UUID.randomUUID();
		UUID professionalId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);

		AppointmentRecord initialRecord = new AppointmentRecord(
				appointmentId, orgId, branchId, professionalId, "Dra. Ana", "NUTRITIONIST", "Consultorio 1",
				null, null, 60, 0, clientId, "Juan Perez", "juan@test.com", "5550000",
				startsAt, startsAt.plusMinutes(60), "REQUESTED", null, null, null, null,
				OffsetDateTime.now(), OffsetDateTime.now()
		);
		AppointmentRecord confirmedRecord = new AppointmentRecord(
				appointmentId, orgId, branchId, professionalId, "Dra. Ana", "NUTRITIONIST", "Consultorio 1",
				null, null, 60, 0, clientId, "Juan Perez", "juan@test.com", "5550000",
				startsAt, startsAt.plusMinutes(60), "CONFIRMED", null, null, userId, OffsetDateTime.now(),
				OffsetDateTime.now(), OffsetDateTime.now()
		);

		when(wellnessRepository.appointment(orgId, branchId, appointmentId))
				.thenReturn(Optional.of(initialRecord))
				.thenReturn(Optional.of(confirmedRecord));

		AppointmentRecord result = wellnessService.confirmAppointment(principal, appointmentId, null, "corr-confirm");

		assertThat(result.status()).isEqualTo("CONFIRMED");
		verify(wellnessRepository).updateAppointmentScheduleAndStatus(eq(orgId), eq(branchId), eq(appointmentId), eq(startsAt), any(), any(), eq("CONFIRMED"), eq(userId));
		verify(auditService).record(principal, "WELLNESS_APPOINTMENT_CONFIRMED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", null, "corr-confirm");
	}
}
