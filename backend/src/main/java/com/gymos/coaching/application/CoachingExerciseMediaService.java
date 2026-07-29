package com.gymos.coaching.application;

import com.gymos.audit.api.AuditService;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.common.storage.ObjectStorageService.StoredObject;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingExerciseMediaService {
	private static final long MAX_SIZE = 8L * 1024 * 1024;
	private static final Set<String> CONTENT_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4"
	);
	private final CoachingCatalogRepository repository;
	private final ObjectStorageService storage;
	private final AuditService auditService;

	public CoachingExerciseMediaService(
			CoachingCatalogRepository repository, ObjectStorageService storage, AuditService auditService
	) {
		this.repository = repository;
		this.storage = storage;
		this.auditService = auditService;
	}

	@Transactional
	public ExerciseRecord upload(
			GymOsPrincipal principal, UUID exerciseId, UploadExerciseMedia command, String correlationId
	) {
		ExerciseRecord exercise = requireManageable(principal, exerciseId);
		validate(command);
		String key = objectKey(principal.organizationId(), exerciseId, command.filename());
		storage.putObject(key, command.contentType(), command.size(), command.content());
		try {
			repository.updateExerciseMedia(principal.organizationId(), exerciseId, key);
		} catch (RuntimeException exception) {
			try {
				storage.removeObject(key);
			} catch (RuntimeException ignored) {
				// The primary persistence error remains the actionable failure.
			}
			throw exception;
		}
		removePrevious(exercise.mediaUrl());
		auditService.record(principal, "EXERCISE_MEDIA_UPDATED", "EXERCISE", exerciseId, "BACKOFFICE", null, correlationId);
		return repository.exercise(principal.organizationId(), exerciseId).orElseThrow();
	}

	@Transactional
	public ExerciseRecord remove(GymOsPrincipal principal, UUID exerciseId, String correlationId) {
		ExerciseRecord exercise = requireManageable(principal, exerciseId);
		repository.updateExerciseMedia(principal.organizationId(), exerciseId, null);
		removePrevious(exercise.mediaUrl());
		auditService.record(principal, "EXERCISE_MEDIA_REMOVED", "EXERCISE", exerciseId, "BACKOFFICE", null, correlationId);
		return repository.exercise(principal.organizationId(), exerciseId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public StoredObject read(GymOsPrincipal principal, UUID exerciseId) {
		requireTenant(principal);
		ExerciseRecord exercise = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		if (exercise.mediaUrl() == null || !exercise.mediaUrl().startsWith("coaching/")) {
			throw new BusinessException("EXERCISE_MEDIA_NOT_FOUND", "El ejercicio no tiene un medio almacenado.");
		}
		return storage.getObject(exercise.mediaUrl());
	}

	private ExerciseRecord requireManageable(GymOsPrincipal principal, UUID exerciseId) {
		requireTenant(principal);
		ExerciseRecord exercise = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		if (exercise.organizationId() == null || "SYSTEM".equals(exercise.origin())) {
			throw new BusinessException("SYSTEM_EXERCISE_IMMUTABLE", "Los ejercicios del sistema son de solo lectura.");
		}
		if (!principal.permissions().contains("exercise.manage")
				&& !repository.exerciseCreatedBy(principal.organizationId(), exercise.id(), principal.userId())) {
			throw new BusinessException("EXERCISE_OUTSIDE_OWNER_SCOPE", "Solo puedes modificar ejercicios creados por ti.");
		}
		return exercise;
	}

	private void validate(UploadExerciseMedia command) {
		if (command.content() == null || command.size() <= 0 || command.size() > MAX_SIZE) {
			throw new BusinessException("EXERCISE_MEDIA_INVALID", "El archivo debe pesar entre 1 byte y 8 MB.");
		}
		String type = command.contentType() == null ? "" : command.contentType().toLowerCase(Locale.ROOT);
		if (!CONTENT_TYPES.contains(type)) {
			throw new BusinessException("EXERCISE_MEDIA_INVALID", "Usa JPG, PNG, WebP, GIF o MP4.");
		}
	}

	private String objectKey(UUID organizationId, UUID exerciseId, String filename) {
		String safe = filename == null || filename.isBlank() ? "exercise-media" : filename;
		safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
		return "coaching/" + organizationId + "/" + exerciseId + "/" + UUID.randomUUID() + "-" + safe;
	}

	private void removePrevious(String reference) {
		if (reference == null || !reference.startsWith("coaching/")) return;
		try {
			storage.removeObject(reference);
		} catch (RuntimeException ignored) {
			// The current database reference is already correct; orphan cleanup is operational debt.
		}
	}

	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar.");
		}
	}

	public record UploadExerciseMedia(String filename, String contentType, long size, InputStream content) {}
}
