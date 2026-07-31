package com.gymos.clients.application;

import java.util.Locale;

public final class SelfServiceIdentifierNormalizer {
	private static final String MEXICO_COUNTRY_CODE = "52";

	private SelfServiceIdentifierNormalizer() {
	}

	public static NormalizedIdentifier normalize(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.contains("@")) {
			return new NormalizedIdentifier(IdentifierType.EMAIL, trimmed.toLowerCase(Locale.ROOT));
		}
		String digits = trimmed.replaceAll("\\D", "");
		if (digits.startsWith("521") && digits.length() == 13) {
			digits = digits.substring(3);
		} else if (digits.startsWith(MEXICO_COUNTRY_CODE) && digits.length() == 12) {
			digits = digits.substring(2);
		}
		return new NormalizedIdentifier(IdentifierType.PHONE, digits);
	}

	public enum IdentifierType {
		EMAIL,
		PHONE
	}

	public record NormalizedIdentifier(IdentifierType type, String value) {
		public boolean valid() {
			return switch (type) {
				case EMAIL -> !value.isBlank() && value.contains("@");
				case PHONE -> value.length() == 10;
			};
		}
	}
}
