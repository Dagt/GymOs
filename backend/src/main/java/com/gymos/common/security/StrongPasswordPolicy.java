package com.gymos.common.security;

import com.gymos.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class StrongPasswordPolicy {
	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	public void validate(String password) {
		boolean valid = password != null
				&& password.length() >= 12
				&& password.length() <= 120
				&& password.chars().anyMatch(Character::isUpperCase)
				&& password.chars().anyMatch(Character::isLowerCase)
				&& password.chars().anyMatch(Character::isDigit)
				&& password.chars().anyMatch(character -> !Character.isLetterOrDigit(character))
				&& password.chars().noneMatch(Character::isWhitespace);
		if (!valid) {
			throw new BusinessException(
					"PASSWORD_POLICY_NOT_MET",
					"Usa al menos 12 caracteres con mayuscula, minuscula, numero y simbolo, sin espacios.");
		}
		// The configured BCrypt encoder rejects longer UTF-8 input, not just longer strings.
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new BusinessException("PASSWORD_POLICY_NOT_MET",
					"La contraseña es demasiado larga. Usa menos caracteres, especialmente si incluyes acentos o emojis.");
		}
	}
}
