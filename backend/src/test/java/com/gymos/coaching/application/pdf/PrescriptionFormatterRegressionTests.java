package com.gymos.coaching.application.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PrescriptionFormatterRegressionTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void formatsStepsWithoutConcatenatingSetsAndRepetitions() throws Exception {
		var node = objectMapper.readTree("""
				{
				  "sets": "3",
				  "reps": "24",
				  "unit": "pasos"
				}
				""");

		assertThat(PrescriptionFormatter.format(node).mainPrescription()).isEqualTo("3 × 24 pasos");
	}

	@Test
	void condensesRepeatedCompoundSets() throws Exception {
		var node = objectMapper.readTree("""
				{
				  "setPlan": [
				    {"segments": [{"reps": "8", "load": "RPE 9"}, {"reps": "15", "load": "RPE 6"}]},
				    {"segments": [{"reps": "8", "load": "RPE 9"}, {"reps": "15", "load": "RPE 6"}]},
				    {"segments": [{"reps": "8", "load": "RPE 9"}, {"reps": "15", "load": "RPE 6"}]}
				  ]
				}
				""");

		assertThat(PrescriptionFormatter.format(node).mainPrescription())
				.isEqualTo("3 × (8 @ RPE 9 + 15 @ RPE 6)");
	}

	@Test
	void condensesDescendingSetsAndRpe() throws Exception {
		var node = objectMapper.readTree("""
				{
				  "setPlan": [
				    {"segments": [{"reps": "12", "load": "RPE 8"}]},
				    {"segments": [{"reps": "10", "load": "RPE 9"}]},
				    {"segments": [{"reps": "8", "load": "RPE 10"}]}
				  ]
				}
				""");

		assertThat(PrescriptionFormatter.format(node).mainPrescription())
				.isEqualTo("12 / 10 / 8 · RPE 8 / RPE 9 / RPE 10");
	}

	@Test
	void keepsOptionalAsVisiblePrescriptionWhenNoOtherPrescriptionExists() throws Exception {
		var node = objectMapper.readTree("{\"optional\": true}");

		var formatted = PrescriptionFormatter.format(node);
		assertThat(formatted.mainPrescription()).isEqualTo("Opcional");
		assertThat(formatted.isOptional()).isTrue();
	}
}
