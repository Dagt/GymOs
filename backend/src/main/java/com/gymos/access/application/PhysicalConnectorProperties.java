package com.gymos.access.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gymos.access.physical-connector")
public record PhysicalConnectorProperties(
		int activationMaxFailures,
		Duration activationFailureWindow,
		int authenticationMaxFailures,
		Duration authenticationFailureWindow,
		Duration lastAuthenticatedWriteInterval
) {
	@ConstructorBinding
	public PhysicalConnectorProperties {
		if (activationMaxFailures <= 0) {
			activationMaxFailures = 8;
		}
		if (activationFailureWindow == null || activationFailureWindow.isNegative()
				|| activationFailureWindow.isZero()) {
			activationFailureWindow = Duration.ofMinutes(5);
		}
		if (authenticationMaxFailures <= 0) {
			authenticationMaxFailures = 12;
		}
		if (authenticationFailureWindow == null || authenticationFailureWindow.isNegative()
				|| authenticationFailureWindow.isZero()) {
			authenticationFailureWindow = Duration.ofMinutes(2);
		}
		if (lastAuthenticatedWriteInterval == null || lastAuthenticatedWriteInterval.isNegative()
				|| lastAuthenticatedWriteInterval.isZero()) {
			lastAuthenticatedWriteInterval = Duration.ofMinutes(5);
		}
	}
}
