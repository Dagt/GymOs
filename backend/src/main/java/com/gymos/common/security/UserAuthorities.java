package com.gymos.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public record UserAuthorities(Set<String> permissions, Set<String> roles) {
	public static UserAuthorities load(JdbcClient jdbc, UUID userId) {
		Set<String> permissions = new HashSet<>();
		Set<String> roles = new HashSet<>();
		jdbc.sql("""
				SELECT r.name, p.code
				FROM user_role ur
				JOIN user_account u ON u.id = ur.user_id
				JOIN role r ON r.id = ur.role_id AND r.account_scope = u.account_scope AND r.deleted_at IS NULL
				 AND (r.organization_id = u.organization_id OR (r.organization_id IS NULL AND r.system_role))
				LEFT JOIN role_permission rp ON rp.role_id = r.id
				LEFT JOIN permission p ON p.id = rp.permission_id AND p.deleted_at IS NULL
				 AND (u.account_scope = 'PLATFORM' OR p.code NOT LIKE 'platform.%')
				WHERE ur.user_id = :userId
				""").param("userId", userId).query((rs, row) -> {
			roles.add(rs.getString("name"));
			String permission = rs.getString("code");
			if (permission != null) permissions.add(permission);
			return row;
		}).list();
		return new UserAuthorities(Set.copyOf(permissions), Set.copyOf(roles));
	}

	public String fingerprint() {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			update(digest, permissions);
			update(digest, roles);
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private void update(MessageDigest digest, Set<String> values) {
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(values.size()).array());
		for (String value : values.stream().sorted().toList()) {
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
			digest.update(bytes);
		}
	}
}
