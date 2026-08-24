package com.gymos.common.security;

import com.gymos.common.api.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class StrongPasswordPolicy {
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
	}
}
