package com.gymos.coaching.application.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailedWorkoutPdfRenderer implements WorkoutPdfRenderer {

	private static final float MARGIN = 40f;
	private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
	private static final float BOTTOM_LIMIT = 52f;

	@Override
	public byte[] render(WorkoutPdfDocument doc, WorkoutPdfTemplate template, String assetOrigin) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Writer writer = new Writer(document, doc, assetOrigin);
			writer.workoutTitle(doc.title(), doc.versionNumber());
			writer.assignmentSummary();

			if (doc.summary() != null && !doc.summary().isBlank()) {
				writer.callout("Indicaciones generales", doc.summary());
			}

			if (doc.sessions() != null) {
				for (WorkoutPdfSession session : doc.sessions()) {
					writer.day(session.dayIndex(), session.name(), session.focus());
					if (session.notes() != null && !session.notes().isBlank()) {
						writer.note("Preparación del día", session.notes());
					}
					if (session.blocks() != null) {
						for (WorkoutPdfBlock block : session.blocks()) {
							writer.block(block.name(), block.type(), block.notes());
							if (block.exercises() != null) {
								for (WorkoutPdfExercise ex : block.exercises()) {
									writer.exercise(ex);
								}
							}
						}
					}
				}
			}

			if (doc.printableNotes() != null && !doc.printableNotes().isBlank()) {
				writer.callout("Indicaciones personales del coach", doc.printableNotes());
			}

			writer.finish();
			document.save(output);
			return output.toByteArray();
		}
	}

	private static final class Writer {
		private final PDDocument document;
		private final WorkoutPdfDocument data;
		private final Color primary;
		private final Color primaryDark;
		private final Color primarySoft;
		private final Color ink = new Color(30, 42, 35);
		private final Color muted = new Color(91, 107, 98);
		private final Color line = new Color(218, 226, 221);
		private final Color surface = new Color(248, 250, 248);
		private final PDImageXObject logo;
		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		private PDPage page;
		private PDPageContentStream stream;
		private float y;

		private Writer(PDDocument document, WorkoutPdfDocument data, String assetOrigin) throws IOException {
			this.document = document;
			this.data = data;
			this.primary = parseColor(data.branding().accentColorHex(), new Color(16, 185, 129));
			this.primaryDark = blend(primary, Color.BLACK, 0.18f);
			this.primarySoft = blend(primary, Color.WHITE, 0.91f);
			this.logo = loadLogo(document, data.branding().logoUrl(), assetOrigin);
			newPage();
		}

		void workoutTitle(String value, int version) throws IOException {
			List<String> titleLines = wrap(value, bold, 25, CONTENT_WIDTH - 90);
			ensure(42 + titleLines.size() * 29);
			writeAt("RUTINA PERSONAL", MARGIN, y, 8, bold, primary);
			writePill("VERSIÓN " + version, PDRectangle.A4.getWidth() - MARGIN - 72, y - 5, 72);
			y -= 27;
			for (String titleLine : titleLines) {
				writeAt(titleLine, MARGIN, y, 25, bold, ink);
				y -= 29;
			}
			y -= 9;
		}

		void assignmentSummary() throws IOException {
			String validity = data.startsOn() == null && data.endsOn() == null
					? "Sin vigencia definida"
					: formatDate(data.startsOn()) + " - " + formatDate(data.endsOn());
			List<Info> values = List.of(
					new Info("SOCIO", value(data.clientName())),
					new Info("COACH", value(data.coachName())),
					new Info("OBJETIVO", value(data.goal())),
					new Info("NIVEL", levelLabel(data.level())),
					new Info("ASIGNADA", formatDate(data.assignedOn())),
					new Info("VIGENCIA", validity)
			);
			int rows = (values.size() + 1) / 2;
			float rowHeight = 43;
			float panelHeight = rows * rowHeight + 10;
			ensure(panelHeight + 14);
			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, y - panelHeight, CONTENT_WIDTH, panelHeight);
			stream.fill();
			stream.setStrokingColor(line);
			stream.setLineWidth(0.45f);
			stream.moveTo(MARGIN + CONTENT_WIDTH / 2, y - 10);
			stream.lineTo(MARGIN + CONTENT_WIDTH / 2, y - panelHeight + 10);
			stream.stroke();
			for (int row = 1; row < rows; row++) {
				float dividerY = y - 5 - row * rowHeight;
				stream.moveTo(MARGIN + 12, dividerY);
				stream.lineTo(MARGIN + CONTENT_WIDTH - 12, dividerY);
				stream.stroke();
			}
			float cardWidth = CONTENT_WIDTH / 2;
			for (int index = 0; index < values.size(); index++) {
				int column = index % 2;
				int row = index / 2;
				float cardX = MARGIN + column * cardWidth;
				float cardTop = y - 5 - row * rowHeight;
				infoCard(values.get(index), cardX, cardTop, cardWidth, rowHeight);
			}
			y -= panelHeight + 14;
		}

		void callout(String label, String value) throws IOException {
			List<String> lines = wrap(value, regular, 9, CONTENT_WIDTH - 32);
			float height = 31 + lines.size() * 12;
			ensure(height + 10);
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
			stream.fill();
			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, y - height, 2, height);
			stream.fill();
			writeAt(label.toUpperCase(Locale.ROOT), MARGIN + 18, y - 17, 8, bold, primaryDark);
			float cursor = y - 34;
			for (String line : lines) {
				writeAt(line, MARGIN + 18, cursor, 9, regular, ink);
				cursor -= 12;
			}
			y -= height + 10;
		}

		void day(int index, String name, String focus) throws IOException {
			ensure(108);
			y -= 9;
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN, y - 43, CONTENT_WIDTH, 43);
			stream.fill();
			writeAt(String.format(Locale.ROOT, "DÍA %02d", index), MARGIN + 16, y - 15, 7, bold, primary);
			writeAt(truncate(name, bold, 14, 305), MARGIN + 16, y - 33, 14, bold, primaryDark);
			if (focus != null && !focus.isBlank()) {
				writeRight(focus.toUpperCase(Locale.ROOT), PDRectangle.A4.getWidth() - MARGIN - 16,
						y - 27, 8, bold, muted);
			}
			y -= 56;
		}

		void block(String name, String type, String notes) throws IOException {
			List<String> noteLines = notes == null || notes.isBlank()
					? List.of()
					: wrap(notes, regular, 8, CONTENT_WIDTH - 20);
			float height = 32 + noteLines.size() * 11;
			ensure(height + 90);
			writeAt(typeLabel(type), MARGIN, y - 8, 7, bold, primary);
			writeAt(truncate(name, bold, 12, CONTENT_WIDTH - 145), MARGIN, y - 24, 12, bold, ink);
			stream.setStrokingColor(line);
			stream.setLineWidth(0.8f);
			stream.moveTo(MARGIN, y - 30);
			stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 30);
			stream.stroke();
			float cursor = y - 43;
			for (String noteLine : noteLines) {
				writeAt(noteLine, MARGIN, cursor, 8, regular, muted);
				cursor -= 11;
			}
			y -= height + 8;
		}

		void note(String label, String value) throws IOException {
			List<String> lines = wrap(value, regular, 8, CONTENT_WIDTH - 24);
			float height = 25 + lines.size() * 11;
			ensure(height + 5);
			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
			stream.fill();
			writeAt(label.toUpperCase(Locale.ROOT), MARGIN + 12, y - 15, 7, bold, muted);
			float cursor = y - 29;
			for (String line : lines) {
				writeAt(line, MARGIN + 12, cursor, 8, regular, ink);
				cursor -= 11;
			}
			y -= height + 8;
		}

		void exercise(WorkoutPdfExercise ex) throws IOException {
			String name = ex.name();
			String prescription = ex.prescriptionText();
			String notes = ex.notesText();
			String details = ex.detailsText();

			List<String> nameLines = wrap(name, bold, 11, CONTENT_WIDTH - 54);
			List<String> detailLines = details.isBlank()
					? List.of()
					: wrap(details, regular, 8, CONTENT_WIDTH - 28);
			List<String> prescriptionLines = prescription.isBlank()
					? List.of()
					: wrap("Prescripción: " + prescription, bold, 9, CONTENT_WIDTH - 28);
			List<String> noteLines = notes.isBlank()
					? List.of()
					: wrap("Técnica: " + notes, regular, 8, CONTENT_WIDTH - 28);

			float boxHeight = 24 + nameLines.size() * 14
					+ (prescriptionLines.size() + detailLines.size() + noteLines.size()) * 12;
			ensure(boxHeight + 10);
			float top = y;
			stream.setStrokingColor(line);
			stream.setLineWidth(0.6f);
			stream.addRect(MARGIN, top - boxHeight, CONTENT_WIDTH, boxHeight);
			stream.stroke();
			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, top - boxHeight, 2, boxHeight);
			stream.fill();
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN + 13, top - 30, 22, 22);
			stream.fill();
			writeCentered(String.valueOf(ex.index()), MARGIN + 24, top - 23, 8, bold, primaryDark);
			float cursor = top - 18;
			for (String nameLine : nameLines) {
				writeAt(nameLine, MARGIN + 48, cursor, 11, bold, ink);
				cursor -= 14;
			}
			cursor -= 3;
			cursor = writeLines(prescriptionLines, MARGIN + 14, cursor, 9, bold, primaryDark, 12);
			cursor = writeLines(detailLines, MARGIN + 14, cursor, 8, regular, muted, 12);
			writeLines(noteLines, MARGIN + 14, cursor, 8, regular, ink, 12);
			y = top - boxHeight - 9;
		}

		void finish() throws IOException {
			if (stream != null) stream.close();
			int totalPages = document.getNumberOfPages();
			for (int index = 0; index < totalPages; index++) {
				PDPage footerPage = document.getPage(index);
				try (PDPageContentStream footer = new PDPageContentStream(
						document, footerPage, PDPageContentStream.AppendMode.APPEND, true, true
				)) {
					footer.setStrokingColor(line);
					footer.setLineWidth(0.6f);
					footer.moveTo(MARGIN, 39);
					footer.lineTo(PDRectangle.A4.getWidth() - MARGIN, 39);
					footer.stroke();
					writeAt(footer, value(data.branding().branchName()), MARGIN, 24, 7, regular, muted);
					writeRight(footer, "Página " + (index + 1) + " de " + totalPages,
							PDRectangle.A4.getWidth() - MARGIN, 24, 7, bold, muted);
				}
			}
		}

		private void ensure(float needed) throws IOException {
			if (y - needed < BOTTOM_LIMIT) {
				newPage();
			}
		}

		private void newPage() throws IOException {
			if (stream != null) stream.close();
			page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			stream = new PDPageContentStream(document, page);
			float top = page.getMediaBox().getHeight();
			if (logo == null) {
				String initials = data.branding().gymName() == null || data.branding().gymName().isBlank()
						? "G" : data.branding().gymName().substring(0, 1).toUpperCase(Locale.ROOT);
				stream.setNonStrokingColor(primarySoft);
				stream.addRect(MARGIN, top - 66, 42, 42);
				stream.fill();
				writeCentered(initials, MARGIN + 21, top - 49, 18, bold, primaryDark);
			} else {
				stream.setNonStrokingColor(surface);
				stream.addRect(MARGIN, top - 66, 42, 42);
				stream.fill();
				drawLogo(logo, MARGIN + 3, top - 63, 36, 36);
			}
			writeAt(value(data.branding().gymName()), MARGIN + 56, top - 39, 14, bold, ink);
			writeAt(value(data.branding().branchName()) + (data.branding().contactPhone() == null ? "" : "  |  " + data.branding().contactPhone()),
					MARGIN + 56, top - 56, 8, regular, muted);
			writePill("PLAN DE ENTRENAMIENTO", PDRectangle.A4.getWidth() - MARGIN - 118, top - 45, 118);
			stream.setStrokingColor(line);
			stream.setLineWidth(0.6f);
			stream.moveTo(MARGIN, top - 78);
			stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, top - 78);
			stream.stroke();
			y = top - 104;
		}

		private void infoCard(Info info, float x, float top, float width, float height) throws IOException {
			writeAt(info.label(), x + 14, top - 14, 7, bold, muted);
			List<String> valueLines = wrap(info.value(), bold, 9, width - 24);
			float cursor = top - 29;
			for (String valueLine : valueLines.stream().limit(2).toList()) {
				writeAt(valueLine, x + 14, cursor, 9, bold, ink);
				cursor -= 11;
			}
		}

		private PDImageXObject loadLogo(PDDocument doc, String logoUrl, String assetOrigin) {
			if (logoUrl == null || logoUrl.isBlank()) return null;
			try {
				String targetUrl = logoUrl;
				if (logoUrl.startsWith("/") && assetOrigin != null && !assetOrigin.isBlank()) {
					targetUrl = assetOrigin.replaceAll("/+$", "") + logoUrl;
				}
				URL url = URI.create(targetUrl).toURL();
				try (InputStream in = url.openStream()) {
					return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "logo");
				}
			} catch (Exception e) {
				return null;
			}
		}

		private void drawLogo(PDImageXObject image, float x, float y, float maxWidth, float maxHeight)
				throws IOException {
			float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
			float width = image.getWidth() * scale;
			float height = image.getHeight() * scale;
			float drawX = x + (maxWidth - width) / 2;
			float drawY = y + (maxHeight - height) / 2;
			stream.drawImage(image, drawX, drawY, width, height);
		}

		private float writeLines(
				List<String> lines, float x, float startY, float size, PDType1Font font, Color color, float leading
		) throws IOException {
			float cursor = startY;
			for (String line : lines) {
				writeAt(line, x, cursor, size, font, color);
				cursor -= leading;
			}
			return cursor;
		}

		private void writePill(String value, float x, float centerY, float width) throws IOException {
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(x, centerY - 11, width, 19);
			stream.fill();
			writeCentered(value, x + width / 2, centerY - 5, 7, bold, primaryDark);
		}

		private void writeAt(
				String value, float x, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			writeAt(stream, value, x, positionY, size, font, color);
		}

		private void writeAt(
				PDPageContentStream target, String value, float x, float positionY,
				float size, PDType1Font font, Color color
		) throws IOException {
			target.setNonStrokingColor(color);
			target.beginText();
			target.setFont(font, size);
			target.newLineAtOffset(x, positionY);
			target.showText(safePdfText(value == null ? "" : value));
			target.endText();
		}

		private void writeRight(
				String value, float right, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			writeRight(stream, value, right, positionY, size, font, color);
		}

		private void writeRight(
				PDPageContentStream target, String value, float right, float positionY,
				float size, PDType1Font font, Color color
		) throws IOException {
			String safe = safePdfText(value);
			float width = textWidth(safe, font, size);
			writeAt(target, safe, right - width, positionY, size, font, color);
		}

		private void writeCentered(
				String value, float centerX, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			String safe = safePdfText(value);
			writeAt(safe, centerX - textWidth(safe, font, size) / 2, positionY, size, font, color);
		}

		private List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			List<String> lines = new ArrayList<>();
			for (String paragraph : safePdfText(value == null ? "" : value).replace('\r', ' ').split("\\n")) {
				StringBuilder current = new StringBuilder();
				for (String word : paragraph.split("\\s+")) {
					String candidate = current.isEmpty() ? word : current + " " + word;
					if (textWidth(candidate, font, size) > maxWidth && !current.isEmpty()) {
						lines.add(current.toString());
						current.setLength(0);
					}
					if (!current.isEmpty()) current.append(' ');
					current.append(word);
				}
				if (!current.isEmpty()) lines.add(current.toString());
			}
			return lines.isEmpty() ? List.of("") : lines;
		}

		private String truncate(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			String safe = safePdfText(value);
			if (textWidth(safe, font, size) <= maxWidth) return safe;
			String suffix = "...";
			int end = safe.length();
			while (end > 1 && textWidth(safe.substring(0, end) + suffix, font, size) > maxWidth) end--;
			return safe.substring(0, end).stripTrailing() + suffix;
		}

		private float textWidth(String value, PDType1Font font, float size) throws IOException {
			return font.getStringWidth(safePdfText(value)) / 1000f * size;
		}

		private String formatDate(LocalDate date) {
			if (date == null) return "No definida";
			return date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX")));
		}

		private String levelLabel(String level) {
			if (level == null) return "General";
			return switch (level.toUpperCase(Locale.ROOT)) {
				case "BEGINNER" -> "Principiante";
				case "INTERMEDIATE" -> "Intermedio";
				case "ADVANCED" -> "Avanzado";
				default -> "General";
			};
		}

		private String typeLabel(String type) {
			if (type == null) return "BLOQUE";
			return switch (type.toUpperCase(Locale.ROOT)) {
				case "WARMUP" -> "CALENTAMIENTO";
				case "MAIN" -> "TRABAJO PRINCIPAL";
				case "SUPERSET" -> "SUPERSERIE";
				case "CIRCUIT" -> "CIRCUITO";
				case "FINISHER" -> "FINALIZADOR";
				case "COOLDOWN" -> "VUELTA A LA CALMA";
				default -> "BLOQUE";
			};
		}

		private static Color parseColor(String hex, Color fallback) {
			if (hex == null || hex.isBlank()) return fallback;
			try {
				String clean = hex.trim().replace("#", "");
				if (clean.length() == 6) {
					return new Color(
							Integer.parseInt(clean.substring(0, 2), 16),
							Integer.parseInt(clean.substring(2, 4), 16),
							Integer.parseInt(clean.substring(4, 6), 16)
					);
				}
			} catch (Exception ignored) {}
			return fallback;
		}

		private static Color blend(Color from, Color to, float ratio) {
			float safeRatio = Math.max(0, Math.min(1, ratio));
			return new Color(
					Math.round(from.getRed() + (to.getRed() - from.getRed()) * safeRatio),
					Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * safeRatio),
					Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * safeRatio)
			);
		}

		private String value(String val) { return val == null || val.isBlank() ? "-" : val.trim(); }

		private String safePdfText(String value) {
			if (value == null) return "";
			return Normalizer.normalize(value, Normalizer.Form.NFC)
					.replace('—', '-')
					.replace('–', '-')
					.replace('•', '-')
					.replaceAll("[^\\x00-\\u00FF]", "?");
		}

		private record Info(String label, String value) {}
	}
}
