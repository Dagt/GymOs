package com.gymos.coaching.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.command.CreateWorkoutCommand;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachIdentity;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachProfileRow;
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

	@Test
	void coachProfilesAggregateActiveBranchesForTheSameUser() {
		GymOsPrincipal principal = principal(Set.of("coach.profile.manage"));
		UUID secondBranchId = UUID.randomUUID();
		UUID coachUserId = UUID.randomUUID();
		when(repository.accessibleBranchIds(organizationId, userId))
				.thenReturn(List.of(branchId, secondBranchId));
		when(repository.coachProfileRows(organizationId, List.of(branchId, secondBranchId)))
				.thenReturn(List.of(
						profileRow(coachUserId, branchId, "Centro", "ACTIVE"),
						profileRow(coachUserId, secondBranchId, "Norte", "ACTIVE")
				));

		var profiles = service.coachProfiles(principal);

		assertThat(profiles).hasSize(1);
		assertThat(profiles.getFirst().status()).isEqualTo("ACTIVE");
		assertThat(profiles.getFirst().specialties()).containsExactly("Fuerza", "Movilidad");
		assertThat(profiles.getFirst().branches()).extracting(branch -> branch.name())
				.containsExactly("Centro", "Norte");
	}

	@Test
	void activeCoachProfileRequiresAnAccessibleBranch() {
		GymOsPrincipal principal = principal(Set.of("coach.profile.manage"));
		UUID coachUserId = UUID.randomUUID();
		UUID inaccessibleBranch = UUID.randomUUID();
		when(repository.accessibleBranchIds(organizationId, userId)).thenReturn(List.of(branchId));
		when(repository.coachProfileRows(organizationId, List.of(branchId)))
				.thenReturn(List.of(profileRow(coachUserId, branchId, "Centro", "ACTIVE")));
		when(repository.userHasCoachRole(organizationId, coachUserId)).thenReturn(true);

		assertThatThrownBy(() -> service.updateCoachProfile(
				principal, coachUserId,
				new CoachingService.UpdateCoachProfileCommand(
						List.of("Fuerza"), "Biografía", "ACTIVE", List.of(inaccessibleBranch)
				),
				"test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("fuera de tu alcance");
	}

	@Test
	void updateCoachProfileSynchronizesProfileAndBranchAccess() {
		GymOsPrincipal principal = principal(Set.of("coach.profile.manage"));
		UUID secondBranchId = UUID.randomUUID();
		UUID coachUserId = UUID.randomUUID();
		List<UUID> managedBranches = List.of(branchId, secondBranchId);
		when(repository.accessibleBranchIds(organizationId, userId)).thenReturn(managedBranches);
		when(repository.coachProfileRows(organizationId, managedBranches))
				.thenReturn(
						List.of(profileRow(coachUserId, branchId, "Centro", "ACTIVE")),
						List.of(profileRow(coachUserId, secondBranchId, "Norte", "ACTIVE"))
				);
		when(repository.userHasCoachRole(organizationId, coachUserId)).thenReturn(true);
		when(repository.coachIdentity(organizationId, coachUserId))
				.thenReturn(Optional.of(new CoachIdentity(
						coachUserId, "Coach Uno", "coach@example.com", "555-0100"
				)));

		var updated = service.updateCoachProfile(
				principal, coachUserId,
				new CoachingService.UpdateCoachProfileCommand(
						List.of(" Fuerza ", "Movilidad"), "Trabajo funcional",
						"ACTIVE", List.of(secondBranchId)
				),
				"test"
		);

		assertThat(updated.branches()).extracting(branch -> branch.id())
				.containsExactly(secondBranchId);
		verify(repository).updateCoachProfileDetails(
				eq(organizationId), eq(coachUserId), eq("[\"Fuerza\",\"Movilidad\"]"),
				eq("Trabajo funcional"), eq(userId), any()
		);
		verify(repository).upsertCoachBranchProfile(
				eq(organizationId), eq(secondBranchId), any(), eq("[\"Fuerza\",\"Movilidad\"]"),
				eq("Trabajo funcional"), eq(userId), any()
		);
		verify(repository).deactivateCoachBranches(
				eq(organizationId), eq(coachUserId), eq(managedBranches),
				eq(List.of(secondBranchId)), eq(userId), any()
		);
		verify(repository).replaceCoachBranchAccess(
				coachUserId, managedBranches, List.of(secondBranchId)
		);
	}

	@Test
	void inactiveCoachProfileKeepsItsAssignedBranchesForTeamManagement() {
		GymOsPrincipal principal = principal(Set.of("coach.profile.manage"));
		UUID coachUserId = UUID.randomUUID();
		List<UUID> managedBranches = List.of(branchId);
		when(repository.accessibleBranchIds(organizationId, userId)).thenReturn(managedBranches);
		when(repository.coachProfileRows(organizationId, managedBranches))
				.thenReturn(
						List.of(profileRow(coachUserId, branchId, "Centro", "ACTIVE")),
						List.of(profileRow(coachUserId, branchId, "Centro", "INACTIVE"))
				);
		when(repository.userHasCoachRole(organizationId, coachUserId)).thenReturn(true);
		when(repository.coachIdentity(organizationId, coachUserId))
				.thenReturn(Optional.of(new CoachIdentity(
						coachUserId, "Coach Uno", "coach@example.com", "555-0100"
				)));

		var updated = service.updateCoachProfile(
				principal, coachUserId,
				new CoachingService.UpdateCoachProfileCommand(
						List.of("Fuerza"), "Trabajo funcional",
						"INACTIVE", List.of(branchId)
				),
				"test"
		);

		assertThat(updated.status()).isEqualTo("INACTIVE");
		assertThat(updated.branches()).extracting(branch -> branch.id()).containsExactly(branchId);
		verify(repository).replaceCoachBranchAccess(coachUserId, managedBranches, List.of(branchId));
		verify(repository).deactivateCoachBranches(
				eq(organizationId), eq(coachUserId), eq(managedBranches),
				eq(List.of()), eq(userId), any()
		);
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

	private CoachProfileRow profileRow(
			UUID coachUserId, UUID profileBranchId, String branchName, String status
	) {
		return new CoachProfileRow(
				coachUserId, "Coach Uno", "coach@example.com", "555-0100",
				"[\"Fuerza\",\"Movilidad\"]", "Trabajo funcional", status,
				profileBranchId, branchName
		);
	}
}
