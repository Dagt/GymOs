package com.gymos.common.api;

import org.springframework.security.core.AuthenticationException;

/** Authentication failure that can expose a stable API code without leaking credentials. */
public class ApiAuthenticationException extends AuthenticationException {
	private final String code;
	private final String publicMessage;

	public ApiAuthenticationException(String code, String publicMessage) {
		super(publicMessage);
		this.code = code;
		this.publicMessage = publicMessage;
	}

	public String code() {
		return code;
	}

	public String publicMessage() {
		return publicMessage;
	}
}
