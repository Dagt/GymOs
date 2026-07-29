package com.gymos.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.CoachingExerciseMediaService.UploadExerciseMedia;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CoachingExerciseMediaServiceTests {
	private CoachingCatalogRepository repository;
	private ObjectStorageService storage;
	private CoachingExerciseMediaService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		repository = mock(CoachingCatalogRepository.class);
		storage = mock(ObjectStorageService.class);
		service = new CoachingExerciseMediaService(repository, storage, mock(AuditService.class));
		principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"coach", "Coach", Set.of("exercise.create")
		);
	}

	@Test
	void coachCanUploadValidatedMediaToAnOwnedExercise() {
		UUID exerciseId = UUID.randomUUID();
		ExerciseRecord exercise = exercise(exerciseId, null);
		when(repository.exercise(principal.organizationId(), exerciseId))
				.thenReturn(Optional.of(exercise));
		when(repository.exerciseCreatedBy(principal.organizationId(), exerciseId, principal.userId()))
				.thenReturn(true);
		when(storage.putObject(any(), eq("image/webp"), eq(4L), any())).thenAnswer(call -> call.getArgument(0));

		service.upload(
				principal, exerciseId,
				new UploadExerciseMedia(
						"demostración.webp", "image/webp", 4,
						new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })
				),
				"test"
		);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(repository).updateExerciseMedia(eq(principal.organizationId()), eq(exerciseId), key.capture());
		assertThat(key.getValue()).startsWith("coaching/" + principal.organizationId() + "/" + exerciseId + "/");
		assertThat(key.getValue()).endsWith("-demostraci_n.webp");
	}

	@Test
	void unsupportedOrOversizedMediaIsRejectedBeforeStorage() {
		UUID exerciseId = UUID.randomUUID();
		when(repository.exercise(principal.organizationId(), exerciseId))
				.thenReturn(Optional.of(exercise(exerciseId, null)));
		when(repository.exerciseCreatedBy(principal.organizationId(), exerciseId, principal.userId()))
				.thenReturn(true);

		assertThatThrownBy(() -> service.upload(
				principal, exerciseId,
				new UploadExerciseMedia(
						"archivo.exe", "application/octet-stream", 4,
						new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })
				),
				"test"
		)).isInstanceOf(BusinessException.class).hasMessageContaining("JPG");
	}

	@Test
	void coachCannotChangeAnotherUsersExercise() {
		UUID exerciseId = UUID.randomUUID();
		when(repository.exercise(principal.organizationId(), exerciseId))
				.thenReturn(Optional.of(exercise(exerciseId, null)));
		when(repository.exerciseCreatedBy(principal.organizationId(), exerciseId, principal.userId()))
				.thenReturn(false);

		assertThatThrownBy(() -> service.remove(principal, exerciseId, "test"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("creados por ti");
	}

	private ExerciseRecord exercise(UUID id, String mediaUrl) {
		return new ExerciseRecord(
				id, principal.organizationId(), "Press inclinado", null, null,
				"Pecho", null, "Empuje", "STRENGTH", "Barra", null, null,
				mediaUrl, null, "COACH", "ACTIVE", principal.userId(),
				OffsetDateTime.now(), OffsetDateTime.now()
		);
	}
}
