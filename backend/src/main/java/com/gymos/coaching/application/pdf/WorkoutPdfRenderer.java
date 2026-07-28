package com.gymos.coaching.application.pdf;

import java.io.IOException;

public interface WorkoutPdfRenderer {
	byte[] render(WorkoutPdfDocument document, WorkoutPdfTemplate template, String assetOrigin) throws IOException;
}
