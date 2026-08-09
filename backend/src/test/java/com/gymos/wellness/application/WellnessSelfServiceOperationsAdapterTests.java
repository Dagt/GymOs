package com.gymos.wellness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WellnessSelfServiceOperationsAdapterTests {
	@Mock
	private WellnessService wellnessService;

	private WellnessSelfServiceOperationsAdapter adapter;
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID professionalId = UUID.randomUUID();
	private final UUID freeServiceId = UUID.randomUUID();
	private final UUID paidServiceId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		adapter = new WellnessSelfServiceOperationsAdapter(wellnessService);
	}

	@Test
	void servicesExposeOnlyActiveFreeOptions() {
		when(wellnessService.services(any(), eq(professionalId))).thenReturn(List.of(
				service(freeServiceId, 0, "ACTIVE"),
				service(paidServiceId, 45000, "ACTIVE"),
				service(UUID.randomUUID(), 0, "INACTIVE")
		));

		var result = adapter.services(organizationId, branchId, professionalId);

		assertThat(result).extracting(item -> item.id()).containsExactly(freeServiceId);
	}

	@Test
	void availabilityRejectsPaidServiceBeforeReadingSchedule() {
		when(wellnessService.services(any(), eq(professionalId))).thenReturn(List.of(
				service(paidServiceId, 45000, "ACTIVE")
		));

		assertThatThrownBy(() -> adapter.availability(
				organizationId, branchId, professionalId, paidServiceId, LocalDate.now()
		))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("SELF_SERVICE_WELLNESS_UNAVAILABLE");
		verify(wellnessService, never()).availabilitySlots(any(), any(), any(), any());
	}

	private ProfessionalServiceRecord service(UUID id, long priceCents, String status) {
		return new ProfessionalServiceRecord(
				id, professionalId, "Consulta", "CONSULTATION", 30, priceCents,
				"MXN", status, OffsetDateTime.now(), OffsetDateTime.now()
		);
	}
}
