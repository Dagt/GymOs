package com.gymos.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gymos.identity")
public record IdentityProperties(
		long invitationHours,
		String frontendBaseUrl
) {
	@ConstructorBinding
	public IdentityProperties {
		if (invitationHours <= 0) invitationHours = 72;
		if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) frontendBaseUrl = "http://localhost:3000";
		frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
	}
}
