package com.gymos.notifications.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class NotificationRecipientHasher {
	private final byte[] key;

	NotificationRecipientHasher(NotificationProperties properties) {
		this.key = properties.preferenceKey().getBytes(StandardCharsets.UTF_8);
	}

	String hash(String recipient) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(
					recipient.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
			));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HmacSHA256 no esta disponible.", exception);
		}
	}
}
