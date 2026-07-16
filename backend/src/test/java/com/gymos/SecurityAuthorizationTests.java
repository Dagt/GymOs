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
	@WithMockUser(authorities = "coach.read")
	void coachCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/coaches").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "workout.read")
	void workoutAssignmentRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/workouts/99999999-9999-9999-9999-999999999952/assign")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "professional.read")
	void professionalCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/professionals").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "automation.read")
	void automationCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/automations").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "notification.read")
	void notificationCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/notifications").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "client.create")
	void clientImportRequiresImportPermission() throws Exception {
		mockMvc.perform(post("/api/v1/clients/imports").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "organization.update")
	void onboardingRequiresOnboardingPermission() throws Exception {
		mockMvc.perform(post("/api/v1/onboarding/pilot-setup").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "benefit.read")
	void benefitCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/benefits").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "client.read")
	void clientBenefitAssignmentRequiresAssignPermission() throws Exception {
		mockMvc.perform(post("/api/v1/clients/99999999-9999-9999-9999-999999999951/benefits")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "client.benefit.assign")
	void clientBenefitVerificationRequiresVerifyPermission() throws Exception {
		mockMvc.perform(post("/api/v1/client-benefits/99999999-9999-9999-9999-999999999952/verify"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "access.manage")
	void visitPassCreationRequiresVisitCreatePermission() throws Exception {
		mockMvc.perform(post("/api/v1/access/visit-passes").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "visit.create")
	void visitPassConsumeRequiresVisitConsumePermission() throws Exception {
		mockMvc.perform(post("/api/v1/access/visit-passes/99999999-9999-9999-9999-999999999953/consume")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void selfServiceSessionCreationIsPublic() throws Exception {
		var result = mockMvc.perform(post("/api/v1/self-service/terminals/UNKNOWN/sessions"))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}
}
