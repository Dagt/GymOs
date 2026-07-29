package com.gymos.coaching.application.pdf;

public record WorkoutPdfExercise(
		int index,
		String name,
		String prescriptionText,
		String detailsText,
		String notesText,
		boolean optional,
		String mediaUrl,
		String primaryMuscleGroup,
		String equipment
) {
	/**
	 * Compatibility accessor for renderers that use JavaBean-style boolean naming.
	 */
	public boolean isOptional() {
		return optional;
	}
}
