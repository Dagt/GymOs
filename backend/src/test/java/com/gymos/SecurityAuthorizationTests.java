package com.gymos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(authorities = "client.read")
	void saleCreationRequiresSaleCreatePermission() throws Exception {
		mockMvc.perform(post("/api/v1/sales").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "sale.read")
	void salesReportRequiresReportPermission() throws Exception {
		mockMvc.perform(get("/api/v1/sales/reports/summary"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "booking.read")
	void bookingCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/bookings").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void selfServiceSessionCreationIsPublic() throws Exception {
		var result = mockMvc.perform(post("/api/v1/self-service/terminals/UNKNOWN/sessions"))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}
}
