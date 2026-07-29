package com.gymos.coaching.application.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class CompactWorkoutPdfRendererRegressionTests {

    @Test
    void rendersEquipmentAsSeparateAppendixWithoutExerciseNumberColumn() throws Exception {
        WorkoutPdfExercise workoutExercise = new WorkoutPdfExercise(
                1, "Press inclinado", "3 x 12", "", "", false,
                null, "Pecho", "Banco inclinado y mancuernas"
        );
        WorkoutPdfExercise equipmentEntry = new WorkoutPdfExercise(
                1, "Press inclinado", "Banco inclinado y mancuernas", "", "", false,
                null, null, "Banco inclinado y mancuernas"
        );
        WorkoutPdfBlock workoutBlock = new WorkoutPdfBlock(
                "Pecho", "MAIN", "TRABAJO PRINCIPAL", "", List.of(workoutExercise)
        );
        WorkoutPdfBlock equipmentBlock = new WorkoutPdfBlock(
                "Equipo necesario", "EQUIPMENT", "EQUIPO NECESARIO",
                "Cada ejercicio aparece una sola vez.", List.of(equipmentEntry)
        );
        WorkoutPdfSession session = new WorkoutPdfSession(
                1, "Día 1 - Pecho", "Pecho", "", List.of(workoutBlock, equipmentBlock)
        );
        WorkoutPdfDocument document = new WorkoutPdfDocument(
                "assignment-1", "Rutina de prueba", 1,
                "Socio de prueba", "Coach de prueba", "Fuerza", "INTERMEDIATE",
                LocalDate.of(2026, 7, 28), null, null,
                null, null, WorkoutPdfBranding.defaultBranding(), List.of(session)
        );

        byte[] bytes = new CompactWorkoutPdfRenderer().render(
                document, WorkoutPdfTemplate.COMPACT_DARK, null
        );

        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(2, pdf.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String workoutPage = stripper.getText(pdf);
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String appendixPage = stripper.getText(pdf);

            assertFalse(workoutPage.contains("#"));
            assertFalse(workoutPage.contains("EQUIPO Y APARATOS NECESARIOS"));
            assertTrue(appendixPage.contains("ANEXO"));
            assertTrue(appendixPage.contains("APARATO / EQUIPO"));
            assertTrue(appendixPage.contains("Banco inclinado y mancuernas"));
        }
    }
}
