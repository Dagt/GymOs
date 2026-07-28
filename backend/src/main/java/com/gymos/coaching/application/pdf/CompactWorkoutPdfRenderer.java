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

public class CompactWorkoutPdfRenderer implements WorkoutPdfRenderer {

	private static final float MARGIN = 36f;
	private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
	private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
	private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
	private static final float BOTTOM_LIMIT = 48f;

	@Override
	public byte[] render(WorkoutPdfDocument doc, WorkoutPdfTemplate template, String assetOrigin) throws IOException {
		boolean isDark = template == WorkoutPdfTemplate.COMPACT_DARK;

		try (PDDocument pdDoc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Writer writer = new PdfWriterImpl(pdDoc, doc, isDark, assetOrigin);
			writer.drawHeader();
			writer.drawSummary();

			if (doc.summary() != null && !doc.summary().isBlank()) {
				writer.drawCallout("Indicaciones Generales", doc.summary());
			}

			if (doc.sessions() != null) {
				for (WorkoutPdfSession session : doc.sessions()) {
					writer.drawSession(session);
				}
			}

			if (doc.printableNotes() != null && !doc.printableNotes().isBlank()) {
				writer.drawCallout("Indicaciones del Coach", doc.printableNotes());
			}

			writer.finish();
			pdDoc.save(out);
			return out.toByteArray();
		}
	}

	private interface Writer {
		void drawHeader() throws IOException;
		void drawSummary() throws IOException;
		void drawCallout(String label, String content) throws IOException;
		void drawSession(WorkoutPdfSession session) throws IOException;
		void finish() throws IOException;
	}

	private static class PdfWriterImpl implements Writer {
		private final PDDocument pdDoc;
		private final WorkoutPdfDocument doc;
		private final boolean isDark;
		private final Color bg;
		private final Color surface;
		private final Color primary;
		private final Color primarySoft;
		private final Color primaryDark;
		private final Color ink;
		private final Color muted;
		private final Color line;
		private final PDImageXObject logo;

		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

		private PDPage currentPage;
		private PDPageContentStream stream;
		private float y;
		private int pageCount = 0;

		private PdfWriterImpl(PDDocument pdDoc, WorkoutPdfDocument doc, boolean isDark, String assetOrigin) throws IOException {
			this.pdDoc = pdDoc;
			this.doc = doc;
			this.isDark = isDark;

			this.primary = parseColor(doc.branding().primaryColorHex(), new Color(16, 185, 129)); // Default Emerald #10B981
			this.primaryDark = blend(primary, Color.BLACK, 0.25f);

			if (isDark) {
				this.bg = new Color(18, 24, 22);           // #121816
				this.surface = new Color(28, 38, 34);      // #1C2622
				this.primarySoft = new Color(24, 52, 42);  // dark emerald tint
				this.ink = new Color(243, 244, 246);      // #F3F4F6
				this.muted = new Color(156, 163, 175);    // #9CA3AF
				this.line = new Color(45, 58, 52);        // subtle border
			} else {
				this.bg = Color.WHITE;                     // #FFFFFF
				this.surface = new Color(248, 250, 248);  // #F8FAF8
				this.primarySoft = blend(primary, Color.WHITE, 0.90f);
				this.ink = new Color(17, 24, 39);         // #111827
				this.muted = new Color(107, 114, 128);    // #6B7280
				this.line = new Color(229, 231, 235);     // #E5E7EB
			}

			this.logo = loadLogo(pdDoc, doc.branding().logoUrl(), assetOrigin);
			newPage();
		}

		private void newPage() throws IOException {
			if (stream != null) {
				stream.close();
			}
			currentPage = new PDPage(PDRectangle.A4);
			pdDoc.addPage(currentPage);
			pageCount++;
			stream = new PDPageContentStream(pdDoc, currentPage);

			if (isDark) {
				stream.setNonStrokingColor(bg);
				stream.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
				stream.fill();
			}

			y = PAGE_HEIGHT - MARGIN;
		}

		private void ensureSpace(float needed) throws IOException {
			if (y - needed < BOTTOM_LIMIT) {
				newPage();
				drawRunningHeader();
			}
		}

		private void drawRunningHeader() throws IOException {
			writeAt(doc.title() + "  |  v" + doc.versionNumber(), MARGIN, y, 8, bold, muted);
			writeRight(doc.branding().gymName(), PAGE_WIDTH - MARGIN, y, 8, bold, primary);
			y -= 6;
			stream.setStrokingColor(line);
			stream.setLineWidth(0.5f);
			stream.moveTo(MARGIN, y);
			stream.lineTo(PAGE_WIDTH - MARGIN, y);
			stream.stroke();
			y -= 12;
		}

		@Override
		public void drawHeader() throws IOException {
			float headerHeight = 52f;
			ensureSpace(headerHeight);

			// Logo / Initials box on left
			float logoBoxSize = 42f;
			if (logo != null) {
				stream.setNonStrokingColor(surface);
				stream.addRect(MARGIN, y - logoBoxSize, logoBoxSize, logoBoxSize);
				stream.fill();
				drawScaledLogo(logo, MARGIN + 2, y - logoBoxSize + 2, logoBoxSize - 4, logoBoxSize - 4);
			} else {
				String initials = (doc.branding().gymName() == null || doc.branding().gymName().isBlank())
						? "G" : doc.branding().gymName().substring(0, 1).toUpperCase(Locale.ROOT);
				stream.setNonStrokingColor(primarySoft);
				stream.addRect(MARGIN, y - logoBoxSize, logoBoxSize, logoBoxSize);
				stream.fill();
				writeCentered(initials, MARGIN + logoBoxSize / 2, y - logoBoxSize / 2 - 5, 18, bold, primaryDark);
			}

			// Gym & Branch info next to logo
			float leftTextX = MARGIN + logoBoxSize + 12;
			writeAt(value(doc.branding().gymName()), leftTextX, y - 12, 13, bold, ink);
			String sub = value(doc.branding().branchName());
			if (doc.branding().contactPhone() != null && !doc.branding().contactPhone().isBlank()) {
				sub += "  ·  Tel: " + doc.branding().contactPhone();
			}
			writeAt(sub, leftTextX, y - 26, 8, regular, muted);

			// Document title & pill on right
			writeRight(doc.title(), PAGE_WIDTH - MARGIN, y - 12, 12, bold, ink);
			writeRight("PLAN DE ENTRENAMIENTO  ·  V" + doc.versionNumber(), PAGE_WIDTH - MARGIN, y - 26, 8, bold, primary);

			y -= headerHeight;

			// Header separator line
			stream.setStrokingColor(line);
			stream.setLineWidth(0.6f);
			stream.moveTo(MARGIN, y);
			stream.lineTo(PAGE_WIDTH - MARGIN, y);
			stream.stroke();

			y -= 12;
		}

		@Override
		public void drawSummary() throws IOException {
			String validity = (doc.startsOn() == null && doc.endsOn() == null)
					? "Sin vigencia"
					: formatDate(doc.startsOn()) + " al " + formatDate(doc.endsOn());

			String[][] items = {
					{ "SOCIO", value(doc.clientName()), "OBJETIVO", value(doc.goal()) },
					{ "COACH", value(doc.coachName()), "NIVEL", levelLabel(doc.level()) },
					{ "ASIGNADO", formatDate(doc.assignedOn()), "VIGENCIA", validity }
			};

			float rowHeight = 18f;
			float summaryHeight = items.length * rowHeight + 8f;
			ensureSpace(summaryHeight);

			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, y - summaryHeight, CONTENT_WIDTH, summaryHeight);
			stream.fill();

			stream.setStrokingColor(line);
			stream.setLineWidth(0.5f);
			stream.addRect(MARGIN, y - summaryHeight, CONTENT_WIDTH, summaryHeight);
			stream.stroke();

			float cursorY = y - 14f;
			float col1 = MARGIN + 10f;
			float col2 = MARGIN + 65f;
			float col3 = MARGIN + CONTENT_WIDTH / 2 + 10f;
			float col4 = MARGIN + CONTENT_WIDTH / 2 + 65f;

			for (String[] row : items) {
				writeAt(row[0], col1, cursorY, 7, bold, muted);
				writeAt(truncate(row[1], bold, 8f, (CONTENT_WIDTH / 2) - 75f), col2, cursorY, 8, bold, ink);

				writeAt(row[2], col3, cursorY, 7, bold, muted);
				writeAt(truncate(row[3], bold, 8f, (CONTENT_WIDTH / 2) - 75f), col4, cursorY, 8, bold, ink);

				cursorY -= rowHeight;
			}

			y -= summaryHeight + 10f;
		}

		@Override
		public void drawCallout(String label, String content) throws IOException {
			List<String> lines = wrap(content, regular, 8f, CONTENT_WIDTH - 20f);
			float height = 18f + lines.size() * 11f;
			ensureSpace(height + 6f);

			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
			stream.fill();

			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, y - height, 3, height);
			stream.fill();

			writeAt(label.toUpperCase(Locale.ROOT), MARGIN + 10f, y - 12f, 7, bold, primaryDark);
			float curY = y - 23f;
			for (String lineText : lines) {
				writeAt(lineText, MARGIN + 10f, curY, 8, regular, ink);
				curY -= 11f;
			}

			y -= height + 8f;
		}

		@Override
		public void drawSession(WorkoutPdfSession session) throws IOException {
			float sessionBannerHeight = 22f;
			ensureSpace(sessionBannerHeight + 40f); // Ensure banner + at least one block header fits together

			// Session Header Bar
			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, y - sessionBannerHeight, CONTENT_WIDTH, sessionBannerHeight);
			stream.fill();

			String dayTag = String.format(Locale.ROOT, "DÍA %02d", session.dayIndex());
			writeAt(dayTag, MARGIN + 8f, y - 15f, 9, bold, Color.WHITE);
			writeAt("·  " + session.name(), MARGIN + 48f, y - 15f, 10, bold, Color.WHITE);

			if (session.focus() != null && !session.focus().isBlank()) {
				writeRight(session.focus().toUpperCase(Locale.ROOT), PAGE_WIDTH - MARGIN - 8f, y - 14f, 8, bold, Color.WHITE);
			}

			y -= sessionBannerHeight + 4f;

			if (session.notes() != null && !session.notes().isBlank()) {
				drawCallout("Preparación / Movilidad", session.notes());
			}

			if (session.blocks() != null) {
				for (WorkoutPdfBlock block : session.blocks()) {
					drawBlock(block);
				}
			}

			y -= 8f; // Extra space between days
		}

		private void drawBlock(WorkoutPdfBlock block) throws IOException {
			ensureSpace(30f);

			// Block Subheader
			String blockTitle = (block.typeLabel() != null ? block.typeLabel().toUpperCase(Locale.ROOT) : "BLOQUE")
					+ (block.name() != null && !block.name().isBlank() ? " — " + block.name() : "");

			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, y - 16f, CONTENT_WIDTH, 16f);
			stream.fill();

			writeAt(blockTitle, MARGIN + 6f, y - 12f, 8, bold, primary);

			if (block.notes() != null && !block.notes().isBlank()) {
				writeRight(truncate(block.notes(), regular, 7f, 200f), PAGE_WIDTH - MARGIN - 6f, y - 12f, 7, regular, muted);
			}

			y -= 18f;

			// Compact Exercises Table Header
			drawTableHeader();

			if (block.exercises() != null) {
				for (WorkoutPdfExercise ex : block.exercises()) {
					drawExerciseRow(ex);
				}
			}

			y -= 4f;
		}

		private void drawTableHeader() throws IOException {
			ensureSpace(14f);

			float colExX = MARGIN + 22f;
			float colPrescX = MARGIN + 180f;
			float colDetX = MARGIN + 360f;

			writeAt("#", MARGIN + 4f, y - 10f, 7, bold, muted);
			writeAt("EJERCICIO", colExX, y - 10f, 7, bold, muted);
			writeAt("PRESCRIPCIÓN", colPrescX, y - 10f, 7, bold, muted);
			writeAt("DETALLES / NOTAS", colDetX, y - 10f, 7, bold, muted);

			y -= 12f;

			stream.setStrokingColor(line);
			stream.setLineWidth(0.4f);
			stream.moveTo(MARGIN, y);
			stream.lineTo(PAGE_WIDTH - MARGIN, y);
			stream.stroke();

			y -= 3f;
		}

		private void drawExerciseRow(WorkoutPdfExercise ex) throws IOException {
			List<String> nameLines = wrap(ex.name(), bold, 9f, 150f);
			List<String> prescLines = wrap(ex.prescriptionText(), bold, 8.5f, 175f);

			StringBuilder detailsBuilder = new StringBuilder();
			if (ex.detailsText() != null && !ex.detailsText().isBlank()) {
				detailsBuilder.append(ex.detailsText());
			}
			if (ex.notesText() != null && !ex.notesText().isBlank()) {
				if (!detailsBuilder.isEmpty()) detailsBuilder.append("\n");
				detailsBuilder.append("Nota: ").append(ex.notesText());
			}
			List<String> detailLines = wrap(detailsBuilder.toString(), regular, 7.5f, 155f);

			int maxLines = Math.max(nameLines.size(), Math.max(prescLines.size(), detailLines.size()));
			float rowHeight = Math.max(18f, maxLines * 11f + 6f);

			ensureSpace(rowHeight);

			// Zebra alternating row background
			if (ex.index() % 2 == 0) {
				stream.setNonStrokingColor(surface);
				stream.addRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight);
				stream.fill();
			}

			float textY = y - 12f;

			// Badge #
			writeAt(String.valueOf(ex.index()), MARGIN + 5f, textY, 8, bold, primary);

			// Exercise Name
			float curY = textY;
			for (String lineText : nameLines) {
				writeAt(lineText, MARGIN + 22f, curY, 9, bold, ink);
				curY -= 11f;
			}

			// Muscle group / Equipment subtitle
			if (ex.primaryMuscleGroup() != null || ex.equipment() != null) {
				String sub = (ex.primaryMuscleGroup() != null ? ex.primaryMuscleGroup() : "")
						+ (ex.equipment() != null ? " (" + ex.equipment() + ")" : "");
				if (!sub.isBlank()) {
					writeAt(truncate(sub, regular, 7f, 150f), MARGIN + 22f, curY, 7, regular, muted);
				}
			}

			// Prescription
			curY = textY;
			for (String lineText : prescLines) {
				writeAt(lineText, MARGIN + 180f, curY, 8.5f, bold, isDark ? primary : primaryDark);
				curY -= 11f;
			}

			// Details / Notes
			curY = textY;
			for (String lineText : detailLines) {
				writeAt(lineText, MARGIN + 360f, curY, 7.5f, regular, muted);
				curY -= 10f;
			}

			y -= rowHeight;

			// Row bottom line
			stream.setStrokingColor(line);
			stream.setLineWidth(0.3f);
			stream.moveTo(MARGIN + 4f, y);
			stream.lineTo(PAGE_WIDTH - MARGIN - 4f, y);
			stream.stroke();
		}

		@Override
		public void finish() throws IOException {
			if (stream != null) {
				stream.close();
			}

			int totalPages = pdDoc.getNumberOfPages();
			for (int i = 0; i < totalPages; i++) {
				PDPage p = pdDoc.getPage(i);
				try (PDPageContentStream footer = new PDPageContentStream(pdDoc, p, PDPageContentStream.AppendMode.APPEND, true, true)) {
					footer.setStrokingColor(line);
					footer.setLineWidth(0.5f);
					footer.moveTo(MARGIN, 32f);
					footer.lineTo(PAGE_WIDTH - MARGIN, 32f);
					footer.stroke();

					String folio = "FOLIO: " + (doc.id() != null ? doc.id() : "N/A") + "  ·  v" + doc.versionNumber();
					writeAt(footer, folio, MARGIN, 20f, 7, regular, muted);
					writeRight(footer, "Página " + (i + 1) + " de " + totalPages, PAGE_WIDTH - MARGIN, 20f, 7, bold, muted);
				}
			}
		}

		// Helper drawing utilities
		private void writeAt(String text, float x, float positionY, float size, PDType1Font font, Color color) throws IOException {
			writeAt(stream, text, x, positionY, size, font, color);
		}

		private void writeAt(PDPageContentStream target, String text, float x, float positionY, float size, PDType1Font font, Color color) throws IOException {
			target.setNonStrokingColor(color);
			target.beginText();
			target.setFont(font, size);
			target.newLineAtOffset(x, positionY);
			target.showText(safePdfText(text));
			target.endText();
		}

		private void writeRight(String text, float rightX, float positionY, float size, PDType1Font font, Color color) throws IOException {
			writeRight(stream, text, rightX, positionY, size, font, color);
		}

		private void writeRight(PDPageContentStream target, String text, float rightX, float positionY, float size, PDType1Font font, Color color) throws IOException {
			String safe = safePdfText(text);
			float width = textWidth(safe, font, size);
			writeAt(target, safe, rightX - width, positionY, size, font, color);
		}

		private void writeCentered(String text, float centerX, float positionY, float size, PDType1Font font, Color color) throws IOException {
			String safe = safePdfText(text);
			float width = textWidth(safe, font, size);
			writeAt(stream, safe, centerX - width / 2, positionY, size, font, color);
		}

		private List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			if (value == null || value.isBlank()) return List.of();
			List<String> lines = new ArrayList<>();
			for (String paragraph : safePdfText(value).replace('\r', ' ').split("\\n")) {
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
			while (end > 1 && textWidth(safe.substring(0, end) + suffix, font, size) > maxWidth) {
				end--;
			}
			return safe.substring(0, end).stripTrailing() + suffix;
		}

		private float textWidth(String text, PDType1Font font, float size) throws IOException {
			return font.getStringWidth(safePdfText(text)) / 1000f * size;
		}

		private void drawScaledLogo(PDImageXObject img, float x, float y, float maxW, float maxH) throws IOException {
			float scale = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
			float w = img.getWidth() * scale;
			float h = img.getHeight() * scale;
			stream.drawImage(img, x + (maxW - w) / 2, y + (maxH - h) / 2, w, h);
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
			float safe = Math.max(0, Math.min(1, ratio));
			return new Color(
					Math.round(from.getRed() + (to.getRed() - from.getRed()) * safe),
					Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * safe),
					Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * safe)
			);
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

		private String value(String val) {
			return (val == null || val.isBlank()) ? "-" : val.trim();
		}

		private String safePdfText(String text) {
			if (text == null) return "";
			return Normalizer.normalize(text, Normalizer.Form.NFC)
					.replace('—', '-')
					.replace('–', '-')
					.replace('•', '-')
					.replaceAll("[^\\x00-\\u00FF]", "?");
		}
	}
}
