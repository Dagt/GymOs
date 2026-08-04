package com.gymos.payments.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class PaymentProviderEncoding {
	private static final SecureRandom RANDOM = new SecureRandom();

	private PaymentProviderEncoding() {
	}

	static String form(Map<String, ?> values) {
		return values.entrySet().stream()
				.filter(entry -> entry.getValue() != null)
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue().toString()))
				.collect(Collectors.joining("&"));
	}

	static String query(String baseUrl, Map<String, ?> values) {
		String separator = baseUrl.contains("?") ? "&" : "?";
		return baseUrl + separator + form(values);
	}

	static Map<String, Object> values() {
		return new LinkedHashMap<>();
	}

	static String randomUrlSafe(int bytes) {
		byte[] value = new byte[bytes];
		RANDOM.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	static String sha256UrlSafe(String value) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible generar la huella criptografica.", exception);
		}
	}

	static String sha256Hex(String value) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible generar la huella criptografica.", exception);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
