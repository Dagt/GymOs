package com.gymos.selfservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SelfServiceNoStoreFilterTests {
	private final SelfServiceNoStoreFilter filter = new SelfServiceNoStoreFilter();

	@Test
	void addsPrivacyHeadersToSelfServiceResponses() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/self-service/sessions/example/summary");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (incomingRequest, outgoingResponse) -> { };

		filter.doFilter(request, response, chain);

		assertThat(response.getHeader("Cache-Control")).contains("no-store");
		assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
	}

	@Test
	void leavesUnrelatedApiResponsesUntouched() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/clients");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (incomingRequest, outgoingResponse) -> { };

		filter.doFilter(request, response, chain);

		assertThat(response.getHeader("Cache-Control")).isNull();
	}
}
