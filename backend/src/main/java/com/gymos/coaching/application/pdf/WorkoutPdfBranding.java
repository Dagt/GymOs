package com.gymos.coaching.application.pdf;

public record WorkoutPdfBranding(
		String gymName,
		String branchName,
		String contactPhone,
		String logoUrl,
		String primaryColorHex,
		String secondaryColorHex
) {
	public static WorkoutPdfBranding defaultBranding() {
		return new WorkoutPdfBranding(
				"GymOS",
				"Sucursal",
				null,
				null,
				"#10B981",
				"#064E3B"
		);
	}
}
