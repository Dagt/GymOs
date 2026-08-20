package com.gymos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientIpResolverTests {
	@Test
	void ignoresSpoofedForwardingHeadersFromUntrustedPeers() {
		TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.8");
		request.addHeader("X-Forwarded-For", "198.51.100.3");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.8");
	}

	@Test
	void walksTrustedProxyChainFromRightToLeft() {
		TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8,192.168.0.0/16");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.7");
		request.addHeader("X-Forwarded-For", "198.51.100.4, 192.168.1.5");

		assertThat(resolver.resolve(request)).isEqualTo("198.51.100.4");
	}

	@Test
	void supportsForwardedIpv6ValuesWithPorts() {
		TrustedClientIpResolver resolver = new TrustedClientIpResolver("2001:db8:1::/48");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("2001:db8:1::2");
		request.addHeader("Forwarded", "for=\"[2001:db8:2::9]:443\";proto=https");

		assertThat(resolver.resolve(request)).isEqualTo("2001:db8:2::9");
	}
}
