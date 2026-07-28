package com.gymos.coaching.application.pdf;

import java.util.List;

public record WorkoutPdfBlock(
		String name,
		String type,
		String typeLabel,
		String notes,
		List<WorkoutPdfExercise> exercises
) {}
