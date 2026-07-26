package com.gymos.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.CoachingCatalogService.CreateExerciseCommand;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoachingCatalogServiceTests {
	private CoachingCatalogRepository repository;
	private CoachingCatalogService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		repository = mock(CoachingCatalogRepository.class);
		service = new CoachingCatalogService(repository, mock(AuditService.class), new ObjectMapper());
		principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"coach", "Coach", Set.of("exercise.read", "exercise.create")
		);
	}

	@Test
	void exerciseNameIsNormalizedAndCoachOriginIsUsed() {
		UUID exerciseId = UUID.randomUUID();
		ExerciseRecord created = exercise(exerciseId, "Press Inclinado", "COACH");
		when(repository.createExercise(eq(principal.organizationId()), eq(principal.userId()), any()))
				.thenReturn(exerciseId);
		when(repository.exercise(principal.organizationId(), exerciseId)).thenReturn(Optional.of(created));

		ExerciseRecord result = service.createExercise(
				principal,
				new CreateExerciseCommand(
						"  Press Inclinado ", null, null, "Pecho", null,
						"Empuje", "strength", "Barra", null, null, null, null
				),
				"test"
		);

		assertThat(result.name()).isEqualTo("Press Inclinado");
		verify(repository).createExercise(eq(principal.organizationId()), eq(principal.userId()), any());
	}

	@Test
	void duplicateExerciseIsRejectedAcrossSystemAndTenantCatalog() {
		when(repository.activeByNormalizedName(principal.organizationId(), "press inclinado"))
				.thenReturn(Optional.of(exercise(UUID.randomUUID(), "Press inclinado", "SYSTEM")));

		assertThatThrownBy(() -> service.createExercise(
				principal,
				new CreateExerciseCommand(
						"Press Inclinado", null, null, null, null,
						null, "STRENGTH", null, null, null, null, null
				),
				"test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Ya existe");
	}

	@Test
	void reportIncludesActiveCoachesWithoutAssignments() {
		var coachId = UUID.randomUUID();
		when(repository.metrics(eq(principal.organizationId()), eq(principal.branchId()), any(), any()))
				.thenReturn(List.of(new CoachingCatalogRepository.CoachMetric(coachId, "Coach Uno", 0, 0, 0)));
		when(repository.assignments(eq(principal.organizationId()), eq(principal.branchId()), any(), any(), any(), any()))
				.thenReturn(List.of());

		var report = service.assignmentReport(principal, null, null, null, null);

		assertThat(report.activeCoaches()).isEqualTo(1);
		assertThat(report.coachesWithoutAssignments()).isEqualTo(1);
		assertThat(report.assignments()).isZero();
	}

	private ExerciseRecord exercise(UUID id, String name, String origin) {
		return new ExerciseRecord(
				id, "SYSTEM".equals(origin) ? null : principal.organizationId(), name, null, null,
				"Pecho", null, "Empuje", "STRENGTH", "Barra", null, null,
				null, null, origin, "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now()
		);
	}
}
