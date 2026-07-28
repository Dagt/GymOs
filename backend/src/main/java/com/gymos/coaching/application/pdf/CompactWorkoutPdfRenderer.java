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
import java.net.URLConnection;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class CompactWorkoutPdfRenderer implements WorkoutPdfRenderer {

	private static final float MARGIN = 34f;
	private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
	private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
	private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
	private static final float BOTTOM_LIMIT = 48f;
	private static final Pattern DAY_PREFIX = Pattern.compile("(?i)^\\s*d[ií]a\\s+\\d+\\s*[-:·.]?\\s*");

	@Override
	public byte[] render(WorkoutPdfDocument doc, WorkoutPdfTemplate template, String assetOrigin) throws IOException {
		boolean dark = template == WorkoutPdfTemplate.COMPACT_DARK;
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Writer writer = new Writer(pdf, doc, dark, assetOrigin);
			writer.header();
			writer.summary();
			if (notBlank(doc.summary())) writer.callout("Indicaciones generales", doc.summary());
			if (doc.sessions() != null) for (WorkoutPdfSession session : doc.sessions()) writer.session(session);
			if (notBlank(doc.printableNotes())) writer.callout("Indicaciones del coach", doc.printableNotes());
			writer.finish();
			pdf.save(out);
			return out.toByteArray();
		}
	}

	private record Theme(
			Color pageBackground, Color surface, Color alternateSurface,
			Color primaryText, Color secondaryText, Color mutedText,
			Color divider, Color accent, Color accentDark, Color accentSoft,
			Color bannerText, Color logoPlate, Color headerLeft, Color headerRight,
			Color texture
	) {
		static Theme light(Color accent) {
			Color darkAccent = blend(accent, Color.BLACK, .34f);
			return new Theme(
					Color.WHITE,
					new Color(248, 250, 248),
					new Color(244, 247, 245),
					new Color(17, 24, 39),
					new Color(55, 65, 81),
					new Color(107, 114, 128),
					new Color(226, 232, 228),
					accent,
					darkAccent,
					blend(accent, Color.WHITE, .90f),
					Color.WHITE,
					Color.WHITE,
					new Color(8, 13, 12),
					blend(accent, Color.BLACK, .48f),
					blend(accent, Color.WHITE, .58f)
			);
		}

		static Theme dark(Color primary, Color accent) {
			Color readableAccent = ensureReadableAccent(accent);
			Color page = blend(primary, Color.BLACK, .84f);
			Color surface = blend(primary, new Color(34, 34, 34), .76f);
			Color alternate = blend(primary, new Color(50, 50, 50), .70f);
			Color divider = blend(primary, new Color(112, 112, 112), .62f);
			return new Theme(
					page,
					surface,
					alternate,
					new Color(245, 247, 246),
					new Color(202, 211, 206),
					new Color(150, 165, 157),
					divider,
					readableAccent,
					blend(readableAccent, Color.BLACK, .22f),
					blend(readableAccent, page, .82f),
					luminance(readableAccent) > 165 ? Color.BLACK : Color.WHITE,
					Color.WHITE,
					blend(primary, Color.BLACK, .92f),
					blend(primary, Color.BLACK, .78f),
					blend(page, readableAccent, .10f)
			);
		}
	}

	private static final class Writer {
		private final PDDocument pdf;
		private final WorkoutPdfDocument doc;
		private final Theme theme;
		private final boolean dark;
		private final PDImageXObject logo;
		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		private PDPageContentStream stream;
		private float y;

		private Writer(PDDocument pdf, WorkoutPdfDocument doc, boolean dark, String assetOrigin) throws IOException {
			this.pdf = pdf;
			this.doc = doc;
			this.dark = dark;
			Color accent = parseColor(doc.branding().primaryColorHex(), new Color(107, 114, 128));
			Color primary = parseColor(doc.branding().secondaryColorHex(), new Color(17, 17, 17));
			this.theme = dark ? Theme.dark(primary, accent) : Theme.light(accent);
			this.logo = loadLogo(pdf, doc.branding().logoUrl(), assetOrigin);
			newPage(false);
		}

		private void newPage(boolean runningHeader) throws IOException {
			if (stream != null) stream.close();
			PDPage page = new PDPage(PDRectangle.A4);
			pdf.addPage(page);
			stream = new PDPageContentStream(pdf, page);
			paintPageBackground();
			y = PAGE_HEIGHT - MARGIN;
			if (runningHeader) runningHeader();
		}

		private void paintPageBackground() throws IOException {
			fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, theme.pageBackground());
			if (dark) {
				float gradientStart = PAGE_WIDTH * .58f;
				float gradientWidth = PAGE_WIDTH - gradientStart;
				int bands = 12;
				for (int index = 0; index < bands; index++) {
					float ratio = (index + 1f) / bands;
					float x = gradientStart + gradientWidth * index / bands;
					float width = gradientWidth / bands + .5f;
					Color band = blend(theme.pageBackground(), theme.accent(), .032f * ratio);
					fillRect(x, 0, width, PAGE_HEIGHT, band);
				}
				Color diagonal = blend(theme.pageBackground(), theme.accent(), .11f);
				for (float start = -PAGE_HEIGHT; start < PAGE_WIDTH + PAGE_HEIGHT; start += 18f) {
					line(start, 0, start + PAGE_HEIGHT, PAGE_HEIGHT, diagonal, .32f);
				}
			} else {
				float headerBottom = PAGE_HEIGHT - 94f;
				fillRect(0, headerBottom, PAGE_WIDTH * .53f, 94f, theme.headerLeft());
				fillRect(PAGE_WIDTH * .53f, headerBottom, PAGE_WIDTH * .47f, 94f, theme.headerRight());
				Color diagonal = blend(theme.headerRight(), Color.WHITE, .25f);
				for (float start = -94f; start < PAGE_WIDTH + 94f; start += 17f) {
					line(start, headerBottom, start + 94f, PAGE_HEIGHT, diagonal, .35f);
				}
			}
		}

		private void ensure(float needed) throws IOException {
			if (y - needed < BOTTOM_LIMIT) newPage(true);
		}

		private void runningHeader() throws IOException {
			Color titleColor = dark ? theme.secondaryText() : Color.WHITE;
			Color gymColor = dark ? theme.accent() : ensureReadableAccent(theme.accent());
			write(value(doc.title()), MARGIN, y, 8, bold, titleColor);
			writeRight(value(doc.branding().gymName()), PAGE_WIDTH - MARGIN, y, 8, bold, gymColor);
			y -= 7;
			line(MARGIN, y, PAGE_WIDTH - MARGIN, y, dark ? theme.divider() : blend(Color.WHITE, theme.accent(), .45f), .5f);
			y -= 13;
		}

		private void header() throws IOException {
			float logoSize = 50f;
			float headerHeight = 62f;
			ensure(headerHeight);
			fillRect(MARGIN, y - logoSize, logoSize, logoSize, theme.logoPlate());
			strokeRect(MARGIN, y - logoSize, logoSize, logoSize, dark ? theme.divider() : blend(Color.WHITE, theme.accent(), .55f), .6f);
			if (logo != null) drawLogo(logo, MARGIN + 4, y - logoSize + 4, logoSize - 8, logoSize - 8);
			else writeCentered(initial(value(doc.branding().gymName())), MARGIN + logoSize / 2, y - logoSize / 2 - 6, 20, bold, theme.accentDark());

			float infoX = MARGIN + logoSize + 12;
			Color headline = Color.WHITE;
			Color subline = dark ? theme.secondaryText() : new Color(225, 232, 228);
			Color accentText = ensureReadableAccent(theme.accent());
			write(value(doc.branding().gymName()), infoX, y - 14, 13, bold, headline);
			String branch = value(doc.branding().branchName());
			if (notBlank(doc.branding().contactPhone())) branch += " · Tel: " + doc.branding().contactPhone().trim();
			write(branch, infoX, y - 29, 8, regular, subline);
			writeRight(truncate(value(doc.title()), bold, 12, 220f), PAGE_WIDTH - MARGIN, y - 14, 12, bold, headline);
			writeRight("PLAN DE ENTRENAMIENTO · V" + doc.versionNumber(), PAGE_WIDTH - MARGIN, y - 30, 8, bold, accentText);
			y -= headerHeight;
			line(MARGIN, y, PAGE_WIDTH - MARGIN, y, dark ? theme.divider() : blend(Color.WHITE, theme.accent(), .40f), .6f);
			y -= 11;
		}

		private void summary() throws IOException {
			String validity = doc.startsOn() == null && doc.endsOn() == null ? "Sin vigencia" : formatDate(doc.startsOn()) + " al " + formatDate(doc.endsOn());
			String[][] values = {
					{"SOCIO", value(doc.clientName()), "OBJETIVO", value(doc.goal())},
					{"COACH", value(doc.coachName()), "NIVEL", level(doc.level())},
					{"ASIGNADO", formatDate(doc.assignedOn()), "VIGENCIA", validity}
			};
			float height = 60f;
			ensure(height);
			fillRect(MARGIN, y - height, CONTENT_WIDTH, height, theme.surface());
			strokeRect(MARGIN, y - height, CONTENT_WIDTH, height, theme.divider(), .5f);
			float rowY = y - 15;
			float half = CONTENT_WIDTH / 2;
			for (String[] row : values) {
				labelValue(row[0], row[1], MARGIN + 10, MARGIN + 66, rowY, half - 76);
				labelValue(row[2], row[3], MARGIN + half + 10, MARGIN + half + 70, rowY, half - 80);
				rowY -= 18;
			}
			y -= height + 9;
		}

		private void labelValue(String label, String value, float labelX, float valueX, float rowY, float maxWidth) throws IOException {
			write(label, labelX, rowY, 7, bold, theme.mutedText());
			write(truncate(value, bold, 8, maxWidth), valueX, rowY, 8, bold, theme.primaryText());
		}

		private void callout(String label, String content) throws IOException {
			List<String> lines = wrap(content, regular, 8, CONTENT_WIDTH - 24);
			float height = 18 + Math.max(1, lines.size()) * 11;
			ensure(height + 6);
			fillRect(MARGIN, y - height, CONTENT_WIDTH, height, theme.accentSoft());
			fillRect(MARGIN, y - height, 4, height, theme.accent());
			write(label.toUpperCase(Locale.forLanguageTag("es-MX")), MARGIN + 11, y - 12, 7, bold, dark ? theme.accent() : theme.accentDark());
			float current = y - 24;
			for (String line : lines) {
				write(line, MARGIN + 11, current, 8, regular, theme.primaryText());
				current -= 11;
			}
			y -= height + 7;
		}

		private void session(WorkoutPdfSession session) throws IOException {
			ensure(58);
			float bannerHeight = 24;
			fillRect(MARGIN, y - bannerHeight, CONTENT_WIDTH, bannerHeight, theme.accentDark());
			fillRect(MARGIN, y - bannerHeight, 52, bannerHeight, theme.accent());
			write(String.format(Locale.ROOT, "DÍA %02d", session.dayIndex()), MARGIN + 8, y - 16, 9, bold, theme.bannerText());
			String cleanName = cleanSessionName(session.name(), session.dayIndex());
			String focus = notBlank(session.focus()) ? session.focus().trim().toUpperCase(Locale.forLanguageTag("es-MX")) : cleanName.toUpperCase(Locale.forLanguageTag("es-MX"));
			write(truncate(focus, bold, 10, CONTENT_WIDTH - 70), MARGIN + 62, y - 16, 10, bold, theme.bannerText());
			y -= bannerHeight + 4;
			if (notBlank(session.notes())) callout("Preparación / movilidad", session.notes());
			if (session.blocks() != null) for (WorkoutPdfBlock block : session.blocks()) block(block);
			y -= 7;
		}

		private void block(WorkoutPdfBlock block) throws IOException {
			boolean hasNotes = block.exercises() != null && block.exercises().stream().anyMatch(ex -> notBlank(ex.detailsText()) || notBlank(ex.notesText()));
			ensure(32);
			String name = notBlank(block.name()) ? block.name().trim() : block.typeLabel();
			fillRect(MARGIN, y - 17, CONTENT_WIDTH, 17, theme.surface());
			fillRect(MARGIN, y - 17, 4, 17, theme.accent());
			write(name.toUpperCase(Locale.forLanguageTag("es-MX")), MARGIN + 9, y - 12, 8, bold, theme.accent());
			if (notBlank(block.notes())) writeRight(truncate(block.notes(), regular, 7, 210), PAGE_WIDTH - MARGIN - 6, y - 12, 7, regular, theme.mutedText());
			y -= 19;
			tableHeader(hasNotes);
			if (block.exercises() != null) for (WorkoutPdfExercise exercise : block.exercises()) exercise(exercise, hasNotes);
			y -= 4;
		}

		private void tableHeader(boolean hasNotes) throws IOException {
			float prescriptionX = hasNotes ? MARGIN + 208 : MARGIN + 250;
			write("#", MARGIN + 4, y - 9, 7, bold, theme.mutedText());
			write("EJERCICIO", MARGIN + 23, y - 9, 7, bold, theme.mutedText());
			write("PRESCRIPCIÓN", prescriptionX, y - 9, 7, bold, theme.mutedText());
			if (hasNotes) write("DETALLES / NOTAS", MARGIN + 385, y - 9, 7, bold, theme.mutedText());
			y -= 12;
			line(MARGIN, y, PAGE_WIDTH - MARGIN, y, theme.divider(), .4f);
			y -= 3;
		}

		private void exercise(WorkoutPdfExercise exercise, boolean hasNotes) throws IOException {
			float nameWidth = hasNotes ? 175 : 215;
			float prescriptionX = hasNotes ? MARGIN + 208 : MARGIN + 250;
			float prescriptionWidth = hasNotes ? 170 : CONTENT_WIDTH - 246;
			List<String> nameLines = wrap(value(exercise.name()), bold, 9, nameWidth);
			List<String> prescriptionLines = wrap(value(exercise.prescriptionText()), bold, 8.5f, prescriptionWidth);
			String details = details(exercise);
			List<String> detailLines = hasNotes ? wrap(details, regular, 7.5f, 140) : List.of();
			int maxLines = Math.max(nameLines.size(), Math.max(prescriptionLines.size(), detailLines.size()));
			float rowHeight = Math.max(18, maxLines * 11 + 6);
			ensure(rowHeight);
			if (exercise.index() % 2 == 0) fillRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight, theme.alternateSurface());
			float textY = y - 12;
			write(String.valueOf(exercise.index()), MARGIN + 5, textY, 8, bold, theme.accent());
			writeLines(nameLines, MARGIN + 23, textY, 9, bold, theme.primaryText(), 11);
			Color prescriptionColor = exercise.isOptional() ? theme.accent() : theme.primaryText();
			writeLines(prescriptionLines, prescriptionX, textY, 8.5f, bold, prescriptionColor, 11);
			if (hasNotes) writeLines(detailLines, MARGIN + 385, textY, 7.5f, regular, theme.secondaryText(), 10);
			y -= rowHeight;
			line(MARGIN + 4, y, PAGE_WIDTH - MARGIN - 4, y, theme.divider(), .3f);
		}

		private String details(WorkoutPdfExercise exercise) {
			List<String> parts = new ArrayList<>();
			if (notBlank(exercise.detailsText())) parts.add(exercise.detailsText().trim());
			if (notBlank(exercise.notesText())) parts.add(exercise.notesText().trim());
			return String.join(" · ", parts);
		}

		private void finish() throws IOException {
			if (stream != null) stream.close();
			int total = pdf.getNumberOfPages();
			for (int index = 0; index < total; index++) {
				PDPage targetPage = pdf.getPage(index);
				try (PDPageContentStream footer = new PDPageContentStream(pdf, targetPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
					line(footer, MARGIN, 32, PAGE_WIDTH - MARGIN, 32, theme.divider(), .5f);
					write(footer, "FOLIO: " + value(doc.id()) + " · v" + doc.versionNumber(), MARGIN, 20, 7, regular, theme.mutedText());
					writeRight(footer, "Página " + (index + 1) + " de " + total, PAGE_WIDTH - MARGIN, 20, 7, bold, theme.mutedText());
				}
			}
		}

		private PDImageXObject loadLogo(PDDocument target, String logoUrl, String assetOrigin) {
			if (!notBlank(logoUrl)) return null;
			Set<String> candidates = new LinkedHashSet<>();
			String trimmed = logoUrl.trim();
			if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) candidates.add(trimmed);
			if (trimmed.startsWith("/") && notBlank(assetOrigin)) {
				String origin = assetOrigin.replaceAll("/+$", "");
				candidates.add(origin + trimmed);
				candidates.add(origin.replace("localhost", "host.docker.internal") + trimmed);
				candidates.add(origin.replace("127.0.0.1", "host.docker.internal") + trimmed);
				candidates.add(origin.replaceFirst(":3000$", ":8080") + trimmed);
			}
			if (trimmed.startsWith("/")) {
				candidates.add("http://frontend:3000" + trimmed);
				candidates.add("http://host.docker.internal:3000" + trimmed);
				candidates.add("http://localhost:3000" + trimmed);
				candidates.add("http://backend:8080" + trimmed);
				candidates.add("http://localhost:8080" + trimmed);
			}
			for (String candidate : candidates) {
				try {
					URLConnection connection = URI.create(candidate).toURL().openConnection();
					connection.setConnectTimeout(3500);
					connection.setReadTimeout(5000);
					connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,*/*;q=0.5");
					connection.setRequestProperty("User-Agent", "GymOS-Workout-PDF/1.0");
					try (InputStream input = connection.getInputStream()) {
						byte[] bytes = input.readAllBytes();
						if (bytes.length > 32) return PDImageXObject.createFromByteArray(target, bytes, "gym-logo");
					}
				} catch (Exception ignored) {
					// Try the next origin. The initials fallback remains available.
				}
			}
			return null;
		}

		private void drawLogo(PDImageXObject image, float x, float imageY, float maxWidth, float maxHeight) throws IOException {
			float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
			float width = image.getWidth() * scale;
			float height = image.getHeight() * scale;
			stream.drawImage(image, x + (maxWidth - width) / 2, imageY + (maxHeight - height) / 2, width, height);
		}

		private void writeLines(List<String> lines, float x, float startY, float size, PDType1Font font, Color color, float leading) throws IOException {
			float current = startY;
			for (String line : lines) {
				write(line, x, current, size, font, color);
				current -= leading;
			}
		}

		private List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			if (!notBlank(value)) return List.of();
			List<String> result = new ArrayList<>();
			for (String paragraph : safe(value).replace('\r', ' ').split("\\n")) {
				StringBuilder current = new StringBuilder();
				for (String word : paragraph.split("\\s+")) {
					String candidate = current.isEmpty() ? word : current + " " + word;
					if (!current.isEmpty() && width(candidate, font, size) > maxWidth) {
						result.add(current.toString());
						current.setLength(0);
					}
					if (!current.isEmpty()) current.append(' ');
					current.append(word);
				}
				if (!current.isEmpty()) result.add(current.toString());
			}
			return result;
		}

		private String truncate(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			String clean = safe(value);
			if (width(clean, font, size) <= maxWidth) return clean;
			int end = clean.length();
			while (end > 1 && width(clean.substring(0, end) + "...", font, size) > maxWidth) end--;
			return clean.substring(0, end).stripTrailing() + "...";
		}

		private float width(String value, PDType1Font font, float size) throws IOException {
			return font.getStringWidth(safe(value)) / 1000f * size;
		}

		private void write(String value, float x, float textY, float size, PDType1Font font, Color color) throws IOException { write(stream, value, x, textY, size, font, color); }
		private void write(PDPageContentStream target, String value, float x, float textY, float size, PDType1Font font, Color color) throws IOException {
			target.setNonStrokingColor(color);
			target.beginText();
			target.setFont(font, size);
			target.newLineAtOffset(x, textY);
			target.showText(safe(value));
			target.endText();
		}
		private void writeRight(String value, float rightX, float textY, float size, PDType1Font font, Color color) throws IOException { writeRight(stream, value, rightX, textY, size, font, color); }
		private void writeRight(PDPageContentStream target, String value, float rightX, float textY, float size, PDType1Font font, Color color) throws IOException {
			String clean = safe(value);
			write(target, clean, rightX - width(clean, font, size), textY, size, font, color);
		}
		private void writeCentered(String value, float centerX, float textY, float size, PDType1Font font, Color color) throws IOException {
			String clean = safe(value);
			write(clean, centerX - width(clean, font, size) / 2, textY, size, font, color);
		}
		private void fillRect(float x, float rectY, float width, float height, Color color) throws IOException {
			stream.setNonStrokingColor(color);
			stream.addRect(x, rectY, width, height);
			stream.fill();
		}
		private void strokeRect(float x, float rectY, float width, float height, Color color, float lineWidth) throws IOException {
			stream.setStrokingColor(color);
			stream.setLineWidth(lineWidth);
			stream.addRect(x, rectY, width, height);
			stream.stroke();
		}
		private void line(float x1, float y1, float x2, float y2, Color color, float lineWidth) throws IOException { line(stream, x1, y1, x2, y2, color, lineWidth); }
		private void line(PDPageContentStream target, float x1, float y1, float x2, float y2, Color color, float lineWidth) throws IOException {
			target.setStrokingColor(color);
			target.setLineWidth(lineWidth);
			target.moveTo(x1, y1);
			target.lineTo(x2, y2);
			target.stroke();
		}
		private String safe(String value) {
			if (value == null) return "";
			return Normalizer.normalize(value, Normalizer.Form.NFC)
					.replace('—', '-').replace('–', '-').replace('•', '-')
					.replaceAll("[^\\x00-\\u00FF]", "?");
		}
	}

	private static String cleanSessionName(String value, int dayIndex) {
		if (!notBlank(value)) return "Sesión " + dayIndex;
		String clean = DAY_PREFIX.matcher(value.trim()).replaceFirst("").trim();
		return clean.isBlank() ? "Sesión " + dayIndex : clean;
	}
	private static String value(String value) { return notBlank(value) ? value.trim() : "-"; }
	private static String initial(String value) {
		String clean = value(value);
		return clean.equals("-") ? "G" : clean.substring(0, 1).toUpperCase(Locale.ROOT);
	}
	private static String formatDate(LocalDate date) {
		if (date == null) return "No definida";
		return date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX")));
	}
	private static String level(String value) {
		if (!notBlank(value)) return "General";
		return switch (value.toUpperCase(Locale.ROOT)) {
			case "BEGINNER" -> "Principiante";
			case "INTERMEDIATE" -> "Intermedio";
			case "ADVANCED" -> "Avanzado";
			default -> value.trim();
		};
	}
	private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
	private static Color parseColor(String hex, Color fallback) {
		if (!notBlank(hex)) return fallback;
		try {
			String clean = hex.trim().replace("#", "");
			if (clean.length() != 6) return fallback;
			return new Color(Integer.parseInt(clean.substring(0, 2), 16), Integer.parseInt(clean.substring(2, 4), 16), Integer.parseInt(clean.substring(4, 6), 16));
		} catch (RuntimeException ignored) { return fallback; }
	}
	private static Color ensureReadableAccent(Color accent) {
		return luminance(accent) < 115 ? blend(accent, Color.WHITE, .35f) : accent;
	}
	private static double luminance(Color color) {
		return .2126 * color.getRed() + .7152 * color.getGreen() + .0722 * color.getBlue();
	}
	private static Color blend(Color from, Color to, float ratio) {
		float safe = Math.max(0, Math.min(1, ratio));
		return new Color(
				Math.round(from.getRed() + (to.getRed() - from.getRed()) * safe),
				Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * safe),
				Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * safe)
		);
	}
}
