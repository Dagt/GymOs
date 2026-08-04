package com.gymos.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationRecipientHasherTests {
	@Test
	void hmacIsCaseInsensitiveStableAndKeyScoped() {
		var first = new NotificationRecipientHasher(properties("first-secret"));
		var second = new NotificationRecipientHasher(properties("second-secret"));

		assertThat(first.hash("SOCIO@example.com")).isEqualTo(first.hash("socio@example.com"));
		assertThat(first.hash("socio@example.com")).hasSize(64)
				.isNotEqualTo(second.hash("socio@example.com"));
	}

	private NotificationProperties properties(String key) {
		return new NotificationProperties(
				30000, 60000, 50, 120, 5, "http://localhost:3000", key,
				new NotificationProperties.Smtp(false, null, 587, null, null, true, true, false, 10000, 10000, 10000)
		);
	}
}
