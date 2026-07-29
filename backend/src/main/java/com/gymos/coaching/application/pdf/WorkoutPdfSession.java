package com.gymos.coaching.application.pdf;

import java.util.List;

public record WorkoutPdfSession(
		int dayIndex,
		String name,
		String focus,
		String notes,
		List<WorkoutPdfBlock> blocks
) {}
