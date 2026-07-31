package com.gymos.coaching.application.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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

    @Test
    void rendersOrganizationAccentInsteadOfFallingBackToGrayscale() throws Exception {
        Color expectedAccent = new Color(245, 217, 10);
        WorkoutPdfExercise exercise = new WorkoutPdfExercise(
                1, "Press inclinado", "3 x 12", "Descanso: 60 s", "", false,
                null, "Pecho", "Banco inclinado"
        );
        WorkoutPdfDocument document = new WorkoutPdfDocument(
                "assignment-branding", "Hipertrofia superior", 1,
                "Socio de prueba", "Coach de prueba", "Hipertrofia", "ADVANCED",
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 7),
                "Mantener una técnica controlada.", null,
                new WorkoutPdfBranding(
                        "Appx Hub Deportivo", "Centro", "+525555555555", null,
                        "#20191A", "#F5D90A", "#F4F7FB"
                ),
                List.of(new WorkoutPdfSession(
                        1, "Día 1 - Pecho", "Pecho", "",
                        List.of(new WorkoutPdfBlock(
                                "Trabajo principal", "MAIN", "TRABAJO PRINCIPAL", "", List.of(exercise)
                        ))
                ))
        );

        byte[] bytes = new CompactWorkoutPdfRenderer().render(
                document, WorkoutPdfTemplate.COMPACT_DARK, null
        );
        writePreviewWhenRequested(bytes);

        try (var pdf = Loader.loadPDF(bytes)) {
            var image = new PDFRenderer(pdf).renderImageWithDPI(0, 72, ImageType.RGB);
            long accentPixels = 0;
            int expectedRgb = expectedAccent.getRGB() & 0xFFFFFF;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) == expectedRgb) accentPixels++;
                }
            }
            assertTrue(accentPixels > 100, "El PDF debe pintar superficies visibles con el color acento del gimnasio.");
        }
    }

    private void writePreviewWhenRequested(byte[] bytes) throws Exception {
        String configuredPath = System.getProperty("gymos.pdf.preview");
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv("GYMOS_PDF_PREVIEW");
        }
        if (configuredPath == null || configuredPath.isBlank()) return;
        Path output = Path.of(configuredPath).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
    }
}
