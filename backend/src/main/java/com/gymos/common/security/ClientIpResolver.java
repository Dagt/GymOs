package com.gymos.common.security;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolver {
	String resolve(HttpServletRequest request);
}
