package com.gymos.coaching.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.command.CreateWorkoutCommand;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoachingServiceSecurityTests {
	private CoachingRepository repository;
	private CoachingService service;
	private UUID userId;
	private UUID organizationId;
	private UUID branchId;

	@BeforeEach
	void setUp() {
		repository = mock(CoachingRepository.class);
		service = new CoachingService(repository, mock(AuditService.class), new ObjectMapper());
		userId = UUID.randomUUID();
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
	}

	@Test
	void ownReadPermissionAlwaysFiltersByAuthenticatedUser() {
		GymOsPrincipal principal = principal(Set.of("workout.read.own"));
		when(repository.workouts(organizationId, branchId, userId)).thenReturn(List.of());

		service.workouts(principal);

		verify(repository).workouts(organizationId, branchId, userId);
	}

	@Test
	void globalReadPermissionUsesTheBranchScopeWithoutOwnerFilter() {
		GymOsPrincipal principal = principal(Set.of("workout.read.all"));
		when(repository.workouts(organizationId, branchId, null)).thenReturn(List.of());

		service.workouts(principal);

		verify(repository).workouts(organizationId, branchId, null);
	}

	@Test
	void coachCannotCreateAWorkoutForAnotherCoach() {
		GymOsPrincipal principal = principal(Set.of("workout.manage.own", "workout.read.own"));
		UUID ownCoachId = UUID.randomUUID();
		UUID otherCoachId = UUID.randomUUID();
		when(repository.coachByUser(organizationId, branchId, userId))
				.thenReturn(Optional.of(coach(ownCoachId)));

		assertThatThrownBy(() -> service.createWorkout(
				principal,
				new CreateWorkoutCommand(
						otherCoachId, "Rutina ajena", null, "GENERAL", null,
						"{\"sessions\":[]}"
				),
				"test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("propio perfil");
	}

	@Test
	void cancellationRequiresReasonAndReplacedCannotBeSetDirectly() {
		GymOsPrincipal principal = principal(Set.of("workout.manage.own"));

		assertThatThrownBy(() -> service.updateAssignmentStatus(
				principal, UUID.randomUUID(), "CANCELLED", " ", "test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("motivo");

		assertThatThrownBy(() -> service.updateAssignmentStatus(
				principal, UUID.randomUUID(), "REPLACED", null, "test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("inválido");
	}

	@Test
	void coachCannotMutateAnAssignmentOutsideOwnerScope() {
		GymOsPrincipal principal = principal(Set.of("workout.manage.own"));
		UUID assignmentId = UUID.randomUUID();
		when(repository.assignment(
				eq(organizationId), eq(branchId), eq(assignmentId), eq(userId)
		)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateAssignmentStatus(
				principal, assignmentId, "COMPLETED", null, "test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("no existe");
	}

	private GymOsPrincipal principal(Set<String> permissions) {
		return new GymOsPrincipal(
				userId, organizationId, branchId, "coach", "Coach", permissions
		);
	}

	private CoachRecord coach(UUID id) {
		return new CoachRecord(
				id, userId, "Coach Uno", null, null, "[]", null, "ACTIVE",
				OffsetDateTime.now(), OffsetDateTime.now()
		);
	}
}
