package com.gymos.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SecurityProperties properties;

	public JwtService(SecurityProperties properties) {
		this.properties = properties;
	}

	public TokenPair issueTokens(GymOsPrincipal principal, String refreshToken) {
		return new TokenPair(accessToken(principal), refreshToken, properties.accessTokenSeconds());
	}

	public String accessToken(GymOsPrincipal principal) {
		Instant now = Instant.now();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "access");
		payload.put("sub", principal.userId().toString());
		payload.put("username", principal.username());
		payload.put("organizationId", principal.organizationId().toString());
		payload.put("branchId", principal.branchId() == null ? null : principal.branchId().toString());
		payload.put("fullName", principal.fullName());
		payload.put("permissions", principal.permissions().stream().sorted().toList());
		payload.put("roles", principal.roles().stream().sorted().toList());
		payload.put("supportMode", principal.supportMode());
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", now.plusSeconds(properties.accessTokenSeconds()).getEpochSecond());
		return sign(payload);
	}

	public Optional<GymOsPrincipal> parseAccessToken(String token) {
		try {
			Map<String, Object> payload = verify(token);
			if (!"access".equals(payload.get("type"))) {
				return Optional.empty();
			}
			long expiresAt = ((Number) payload.get("exp")).longValue();
			if (Instant.now().getEpochSecond() >= expiresAt) {
				return Optional.empty();
			}
			@SuppressWarnings("unchecked")
			List<String> permissions = (List<String>) payload.get("permissions");
			@SuppressWarnings("unchecked")
			List<String> roles = (List<String>) payload.get("roles");
			String branchId = (String) payload.get("branchId");
			return Optional.of(new GymOsPrincipal(
					UUID.fromString((String) payload.get("sub")),
					UUID.fromString((String) payload.get("organizationId")),
					branchId == null ? null : UUID.fromString(branchId),
					(String) payload.get("username"),
					(String) payload.get("fullName"),
					permissions == null ? Set.of() : Set.copyOf(permissions),
					roles == null ? Set.of() : Set.copyOf(roles),
					Boolean.TRUE.equals(payload.get("supportMode"))
			));
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private String sign(Map<String, Object> payload) {
		try {
			String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
			String body = encodeJson(payload);
			String signature = hmac(header + "." + body);
			return header + "." + body + "." + signature;
		} catch (Exception exception) {
			throw new BusinessException("TOKEN_SIGNING_FAILED", "No fue posible generar el token.");
		}
	}

	private Map<String, Object> verify(String token) throws Exception {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid token");
		}
		String expectedSignature = hmac(parts[0] + "." + parts[1]);
		if (!constantTimeEquals(expectedSignature, parts[2])) {
			throw new IllegalArgumentException("Invalid signature");
		}
		return objectMapper.readValue(DECODER.decode(parts[1]), MAP_TYPE);
	}

	private String encodeJson(Map<String, Object> value) throws Exception {
		return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
	}

	private String hmac(String value) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

	private boolean constantTimeEquals(String left, String right) {
		return MessageDigestSupport.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
	}

	public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
	}

	private static final class MessageDigestSupport {
		private static boolean equals(byte[] left, byte[] right) {
			if (left.length != right.length) {
				return false;
			}
			int result = 0;
			for (int index = 0; index < left.length; index++) {
				result |= left[index] ^ right[index];
			}
			return result == 0;
		}
	}
}
