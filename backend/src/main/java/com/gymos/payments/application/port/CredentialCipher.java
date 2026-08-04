package com.gymos.payments.application.port;

public interface CredentialCipher {
	String encrypt(String plaintext);

	String decrypt(String ciphertext);
}
