package com.gymos.coaching.application.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrescriptionFormatterTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void formatThreeEqualsTwenty() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "20");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 20");
	}

	@Test
	void formatDescendingReps() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "12, 10, 8");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 12 / 10 / 8");
	}

	@Test
	void formatRepsWithProgressionLoad() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "12, 10, 8");
		node.put("load", "subir carga");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 12 / 10 / 8 \u00b7 subir carga");
	}

	@Test
	void formatCompoundSet() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "(8 + 15)");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 (8 + 15)");
	}

	@Test
	void formatBySteps() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "24 pasos");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 24 pasos");
	}

	@Test
	void formatIntensityRpe() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 3);
		node.put("reps", "12");
		node.put("load", "RPE 8");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("3 \u00d7 12 \u00b7 RPE 8");
	}

	@Test
	void formatOptionalExercise() {
		ObjectNode node = mapper.createObjectNode();
		node.put("sets", 2);
		node.put("reps", "15");
		node.put("optional", true);

		var result = PrescriptionFormatter.format(node);
		assertThat(result.isOptional()).isTrue();
		assertThat(result.detailsText()).contains("Opcional");
	}

	@Test
	void formatFaithfulFreePrescriptionText() {
		ObjectNode node = mapper.createObjectNode();
		node.put("prescription", "1x20 (40kg) + 1x15 (50kg) + 1x10 (60kg)");

		var result = PrescriptionFormatter.format(node);
		assertThat(result.mainPrescription()).isEqualTo("1x20 (40kg) + 1x15 (50kg) + 1x10 (60kg)");
	}
}
