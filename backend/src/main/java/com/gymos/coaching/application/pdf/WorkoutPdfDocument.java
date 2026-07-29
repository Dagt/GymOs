package com.gymos.coaching.application.pdf;

import java.time.LocalDate;
import java.util.List;

public record WorkoutPdfDocument(
		String id,
		String title,
		int versionNumber,
		String clientName,
		String coachName,
		String goal,
		String level,
		LocalDate assignedOn,
		LocalDate startsOn,
		LocalDate endsOn,
		String summary,
		String printableNotes,
		WorkoutPdfBranding branding,
		List<WorkoutPdfSession> sessions
) {}
