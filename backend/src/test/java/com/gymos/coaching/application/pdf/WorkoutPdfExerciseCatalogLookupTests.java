package com.gymos.coaching.application.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkoutPdfExerciseCatalogLookupTests {

	@Test
	void infersEquipmentOnlyWhenExerciseNameContainsExplicitEquipment() {
		assertThat(WorkoutPdfExerciseCatalogLookup.inferFromName("Curl banco inclinado"))
				.contains("Banco inclinado y mancuernas");
		assertThat(WorkoutPdfExerciseCatalogLookup.inferFromName("Laterales en polea"))
				.contains("Polea / estación de cables");
		assertThat(WorkoutPdfExerciseCatalogLookup.inferFromName("Press francés"))
				.contains("Barra, mancuerna o polea");
	}

	@Test
	void doesNotInventEquipmentForAmbiguousBodyweightExercise() {
		assertThat(WorkoutPdfExerciseCatalogLookup.inferFromName("Flexiones")).isEmpty();
		assertThat(WorkoutPdfExerciseCatalogLookup.inferFromName("Core")).isEmpty();
	}
}
