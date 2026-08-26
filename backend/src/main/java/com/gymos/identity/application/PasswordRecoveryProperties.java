package com.gymos.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.identity.password-recovery")
public record PasswordRecoveryProperties(long expiryMinutes, long resendCooldownSeconds, int maxAttemptsPerMinute) {
	public PasswordRecoveryProperties {
		if (expiryMinutes <= 0) expiryMinutes = 60;
		if (resendCooldownSeconds <= 0) resendCooldownSeconds = 60;
		if (maxAttemptsPerMinute <= 0) maxAttemptsPerMinute = 10;
	}
}
