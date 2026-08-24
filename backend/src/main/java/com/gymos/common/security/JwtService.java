package com.gymos.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
		long lifetimeSeconds = accessTokenLifetime(principal);
		return new TokenPair(accessToken(principal, lifetimeSeconds), refreshToken, lifetimeSeconds);
	}

	public String accessToken(GymOsPrincipal principal) {
		return accessToken(principal, accessTokenLifetime(principal));
	}

	private String accessToken(GymOsPrincipal principal, long lifetimeSeconds) {
		Instant now = Instant.now();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "access");
		payload.put("sub", principal.userId().toString());
		payload.put("scope", principal.accountScope().name());
		payload.put("username", principal.username());
		payload.put("organizationId", principal.organizationId() == null ? null : principal.organizationId().toString());
		payload.put("branchId", principal.branchId() == null ? null : principal.branchId().toString());
		payload.put("fullName", principal.fullName());
		payload.put("permissions", principal.permissions().stream().sorted().toList());
		payload.put("roles", principal.roles().stream().sorted().toList());
		payload.put("supportMode", principal.supportMode());
		payload.put("supportExpiresAt", principal.supportExpiresAt() == null ? null : principal.supportExpiresAt().getEpochSecond());
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", now.plusSeconds(lifetimeSeconds).getEpochSecond());
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
			AccountScope accountScope = AccountScope.valueOf((String) payload.get("scope"));
			String organizationId = (String) payload.get("organizationId");
			String branchId = (String) payload.get("branchId");
			boolean supportMode = Boolean.TRUE.equals(payload.get("supportMode"));
			Number supportExpiry = (Number) payload.get("supportExpiresAt");
			Instant supportExpiresAt = supportExpiry == null ? null : Instant.ofEpochSecond(supportExpiry.longValue());
			if (!validContext(accountScope, organizationId, branchId, supportMode, supportExpiresAt)) {
				return Optional.empty();
			}
			return Optional.of(new GymOsPrincipal(
					UUID.fromString((String) payload.get("sub")),
					accountScope,
					organizationId == null ? null : UUID.fromString(organizationId),
					branchId == null ? null : UUID.fromString(branchId),
					(String) payload.get("username"),
					(String) payload.get("fullName"),
					permissions == null ? Set.of() : Set.copyOf(permissions),
					roles == null ? Set.of() : Set.copyOf(roles),
					supportMode,
					supportExpiresAt
			));
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private boolean validContext(
			AccountScope accountScope,
			String organizationId,
			String branchId,
			boolean supportMode,
			Instant supportExpiresAt
	) {
		if (accountScope == AccountScope.TENANT) {
			return organizationId != null && !supportMode && supportExpiresAt == null;
		}
		if (!supportMode) {
			return organizationId == null && branchId == null && supportExpiresAt == null;
		}
		return organizationId != null
				&& supportExpiresAt != null
				&& Instant.now().isBefore(supportExpiresAt);
	}

	private long accessTokenLifetime(GymOsPrincipal principal) {
		return isReception(principal)
				? properties.receptionAccessTokenSeconds()
				: properties.accessTokenSeconds();
	}

	private boolean isReception(GymOsPrincipal principal) {
		boolean explicitReceptionRole = principal.roles().stream()
				.map(role -> role.toLowerCase(Locale.ROOT))
				.anyMatch(role -> role.contains("recepcion") || role.contains("recepción") || role.contains("reception"));
		if (explicitReceptionRole) {
			return true;
		}
		boolean elevatedRole = principal.roles().stream()
				.map(role -> role.toLowerCase(Locale.ROOT))
				.anyMatch(role -> role.contains("dueno")
						|| role.contains("dueño")
						|| role.contains("owner")
						|| role.contains("administrador")
						|| role.contains("admin")
						|| role.contains("gerente")
						|| role.contains("manager")
						|| role.contains("coach"));
		return !elevatedRole
				&& principal.permissions().contains("sale.create")
				&& principal.permissions().contains("client.read");
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
