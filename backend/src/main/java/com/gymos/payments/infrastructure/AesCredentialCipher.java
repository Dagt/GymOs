package com.gymos.payments.infrastructure;

import com.gymos.common.api.BusinessException;
import com.gymos.payments.application.PaymentProviderProperties;
import com.gymos.payments.application.port.CredentialCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class AesCredentialCipher implements CredentialCipher {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String DEVELOPMENT_KEY = "dev-only-payment-credential-key-change-in-production";
	private final SecretKeySpec key;

	AesCredentialCipher(PaymentProviderProperties properties) {
		String configured = properties.credentialKey();
		String material = configured == null || configured.isBlank() ? DEVELOPMENT_KEY : configured;
		try {
			this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)), "AES");
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible inicializar el cifrado de proveedores.", exception);
		}
	}

	@Override
	public String encrypt(String plaintext) {
		if (plaintext == null) return null;
		try {
			byte[] nonce = new byte[12];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return "v1:" + Base64.getEncoder().encodeToString(
					ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()
			);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible cifrar la credencial del proveedor.", exception);
		}
	}

	@Override
	public String decrypt(String ciphertext) {
		if (ciphertext == null) return null;
		if (!ciphertext.startsWith("v1:")) {
			throw new BusinessException("PAYMENT_CREDENTIAL_FORMAT_INVALID", "La credencial almacenada no tiene un formato soportado.");
		}
		try {
			byte[] combined = Base64.getDecoder().decode(ciphertext.substring(3));
			byte[] nonce = java.util.Arrays.copyOfRange(combined, 0, 12);
			byte[] encrypted = java.util.Arrays.copyOfRange(combined, 12, combined.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BusinessException("PAYMENT_CREDENTIAL_DECRYPTION_FAILED", "No fue posible abrir la credencial del proveedor.");
		}
	}
}
