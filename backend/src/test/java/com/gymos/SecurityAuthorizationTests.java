package com.gymos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gymos.common.security.UserCredentialVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;
	@Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;
	@Autowired private SecurityFilterChain securityFilterChain;
	@MockitoBean private UserCredentialVersionRepository credentialVersions;

	@BeforeEach
	void activeFixtureCredentials() {
		org.mockito.Mockito.when(credentialVersions.isCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
	}

	@Test
	void documentedOperationsMatchSpringRoutingAndEffectiveAuthorities() throws Exception {
		var documented = new LinkedHashMap<String, String>();
		var row = Pattern.compile("^\\| `([A-Z]+ /api/[^`]+)` \\| (.+?) \\|");
		for (String line : Files.readAllLines(Path.of("..", "docs", "security", "endpoint-permissions.md"))) {
			var match = row.matcher(line);
			if (match.find()) {
				assertThat(documented.put(match.group(1), match.group(2))).as(match.group(1)).isNull();
			}
		}
		var mapped = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
				.filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.gymos."))
				.flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
						.flatMap(method -> entry.getKey().getPatternValues().stream().map(path -> method.name() + " " + path)))
				.collect(Collectors.toSet());
		assertThat(documented.keySet()).containsExactlyInAnyOrderElementsOf(mapped);

		var manager = securityFilterChain.getFilters().stream()
				.filter(AuthorizationFilter.class::isInstance).map(AuthorizationFilter.class::cast)
				.findFirst().orElseThrow().getAuthorizationManager();
		var anonymous = new AnonymousAuthenticationToken("audit", "anonymous",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
		var authenticated = UsernamePasswordAuthenticationToken.authenticated("audit", null, List.of());
		var authority = Pattern.compile("`([a-z][a-z0-9.-]+)`");
		var authorities = documented.values().stream().flatMap(value -> authority.matcher(value).results())
				.map(match -> match.group(1)).collect(Collectors.toSet());
		for (var operation : documented.entrySet()) {
			var parts = operation.getKey().split(" ", 2);
			var request = new MockHttpServletRequest(parts[0],
					parts[1].replaceAll("\\{[^}]+}", "00000000-0000-4000-8000-000000000001"));
			ServletRequestPathUtils.parseAndCache(request);
			String rule = operation.getValue();
			boolean publicRoute = rule.equals("Público") || rule.equals("Credencial del protocolo");
			assertThat(manager.authorize(() -> anonymous, request).isGranted())
					.as("%s as anonymous", operation.getKey()).isEqualTo(publicRoute);
			assertThat(manager.authorize(() -> authenticated, request).isGranted())
					.as("%s without authorities", operation.getKey()).isEqualTo(publicRoute || rule.equals("JWT autenticado"));
			if (!publicRoute && !rule.equals("JWT autenticado")) {
				var permitted = authority.matcher(rule).results().map(match -> match.group(1)).collect(Collectors.toSet());
				assertThat(permitted).as(operation.getKey()).isNotEmpty();
				for (String permission : authorities) {
					var actor = UsernamePasswordAuthenticationToken.authenticated("audit", null,
							List.of(new SimpleGrantedAuthority(permission)));
					assertThat(manager.authorize(() -> actor, request).isGranted())
							.as("%s with %s", operation.getKey(), permission).isEqualTo(permitted.contains(permission));
				}
			}
		}
	}

	@Test
	@WithMockUser(authorities = "platform.admin")
	void unclassifiedOperationsFailClosed() throws Exception {
		mockMvc.perform(get("/api/v1/unclassified-operation")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = {"access.read", "access.manage", "access.manual.open"})
	void physicalAccessPermissionsDoNotGrantManualReceptionAccess() throws Exception {
		mockMvc.perform(get("/api/v1/access/manual-check-ins")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/access/manual-check-ins/clients").param("query", "fixture"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/access/manual-check-ins")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "access.check-in")
	void manualReceptionPermissionDoesNotGrantReversalOrDoorOpening() throws Exception {
		mockMvc.perform(post("/api/v1/access/manual-check-ins/{id}/reversal", UUID.randomUUID()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/access/commands/open")).andExpect(status().isForbidden());
	}

	@Test
	void revokedJwtIsRejectedBeforeController() throws Exception {
		org.mockito.Mockito.when(credentialVersions.isCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(false);
		String token = jwtService.accessToken(new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "coach", "Coach", Set.of("client.read")));
		mockMvc.perform(get("/api/v1/clients/search").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(authorities = "client.read")
	void passwordRecoveryAndBenefitEditionRequireManagementPermissions() throws Exception {
		mockMvc.perform(post("/api/v1/users/internal/99999999-9999-9999-9999-999999999951/password-recovery"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/benefits/99999999-9999-9999-9999-999999999951").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void passwordRecoveryAcceptanceIsPublicButValidatesBodyWithoutEchoingIt() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-recovery/accept").contentType("application/json").content("{\"token\":\"test-sensitive\",\"password\":"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("test-sensitive"));
	}

	@Test
	@WithMockUser(authorities = "client.read")
	void inventoryPagedHistoriesStillRequireInventoryRead() throws Exception {
		for (String path : java.util.List.of("search", "movements/search", "physical-counts/search", "transfers/search")) {
			mockMvc.perform(get("/api/v1/inventory/" + path)).andExpect(status().isForbidden());
		}
	}

	@Test
	void validJwtWithoutOrganizationPermissionReturnsForbiddenInsteadOfInvalidSession() throws Exception {
		String token = jwtService.accessToken(new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("branch.read"),
				Set.of("Dueno del gimnasio"),
				false
		));

		mockMvc.perform(get("/api/v1/organizations/current")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
	}

	@Test
	void organizationUpdateAuthorityCanReadCurrentOrganization() throws Exception {
		String token = jwtService.accessToken(new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("organization.update"),
				Set.of("Dueno del gimnasio"),
				false
		));

		mockMvc.perform(get("/api/v1/organizations/current")
						.header("Authorization", "Bearer " + token))
				.andExpect(result ->
						assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
	}

	@Test
	void organizationLogoReadIsPublic() throws Exception {
		var result = mockMvc.perform(get(
				"/api/v1/organizations/99999999-9999-9999-9999-999999999951/logo"
		)).andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "organization.update")
	void managedOrganizationLogoUploadRequiresPlatformAdministrator() throws Exception {
		mockMvc.perform(multipart(
						"/api/v1/organizations/99999999-9999-9999-9999-999999999951/logo"
				).file("file", new byte[] {1, 2, 3, 4}))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
	}

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
	@WithMockUser(authorities = "sale.read")
	void saleRefundRequiresRefundPermission() throws Exception {
		mockMvc.perform(post("/api/v1/sales/99999999-9999-9999-9999-999999999954/refunds")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "booking.read")
	void bookingCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/bookings").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "booking.manage")
	void bookingPaymentRequiresPaymentManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/99999999-9999-9999-9999-999999999955/payments")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "resource.read")
	void resourceUpdateRequiresManagePermission() throws Exception {
		mockMvc.perform(put("/api/v1/resources/99999999-9999-9999-9999-999999999956")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "resource.read")
	void resourceDeleteRequiresManagePermission() throws Exception {
		mockMvc.perform(delete("/api/v1/resources/99999999-9999-9999-9999-999999999956"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "coach.read")
	void coachCreationRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/coaches").contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "coach.read")
	void advancedCoachProfilesRequireDedicatedPermission() throws Exception {
		mockMvc.perform(get("/api/v1/coaches/profiles"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/coaches/profiles/99999999-9999-9999-9999-999999999952")
						.contentType("application/json")
						.content("{}"))
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
	@WithMockUser(authorities = "workout.manage")
	void legacyWorkoutPermissionNoLongerGrantsAccess() throws Exception {
		mockMvc.perform(get("/api/v1/workouts"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "workout.read.own")
	void assignmentStatusRequiresAssignPermission() throws Exception {
		mockMvc.perform(patch("/api/v1/workout-assignments/99999999-9999-9999-9999-999999999952/status")
						.contentType("application/json")
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "exercise.read")
	void exerciseMutationRequiresCreateOrManagePermission() throws Exception {
		mockMvc.perform(put("/api/v1/exercises/99999999-9999-9999-9999-999999999952")
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
	@WithMockUser(authorities = "organization.read")
	void platformBillingRequiresPlatformAdministrator() throws Exception {
		mockMvc.perform(get("/api/v1/platform/billing/overview"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
	}

	@Test
	@WithMockUser(authorities = "organization.read")
	void platformCustomerManagementRequiresPlatformAdministrator() throws Exception {
		mockMvc.perform(get("/api/v1/platform/customers"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
		mockMvc.perform(put("/api/v1/platform/customers/99999999-9999-9999-9999-999999999951")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
		mockMvc.perform(post("/api/v1/platform/customers/99999999-9999-9999-9999-999999999951/owner-invitation"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
	}

	@Test
	@WithMockUser(authorities = "notification.manage")
	void emailConfigurationRequiresDedicatedSettingsPermission() throws Exception {
		mockMvc.perform(put("/api/v1/notifications/email-configuration")
						.contentType("application/json")
						.content("{\"enabled\":false,\"providerKey\":\"SMTP\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "notification.settings.manage")
	void emailSettingsPermissionCanReachConfigurationEndpoint() throws Exception {
		var read = mockMvc.perform(get("/api/v1/notifications/email-configuration")).andReturn();
		var result = mockMvc.perform(put("/api/v1/notifications/email-configuration")
				.contentType("application/json")
				.content("{\"enabled\":false,\"providerKey\":\"SMTP\"}"))
				.andReturn();
		assertThat(read.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "notification.read")
	void emailPreferenceMutationRequiresClientOrNotificationManagement() throws Exception {
		mockMvc.perform(put("/api/v1/notifications/email-preferences")
				.contentType("application/json")
				.content("{\"recipient\":\"socio@example.com\",\"emailEnabled\":true,\"marketingEnabled\":false}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "client.update")
	void clientEditorsCanReachEmailPreferences() throws Exception {
		var read = mockMvc.perform(get("/api/v1/notifications/email-preferences")
				.param("recipient", "socio@example.com")).andReturn();
		var update = mockMvc.perform(put("/api/v1/notifications/email-preferences")
				.contentType("application/json")
				.content("{\"recipient\":\"socio@example.com\",\"emailEnabled\":true,\"marketingEnabled\":false}"))
				.andReturn();
		assertThat(read.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(update.getResponse().getStatus()).isNotIn(401, 403);
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
	@WithMockUser(authorities = "catalog.read")
	void catalogReadersCanLoadOrganizationAccessOptionsForVisitPackages() throws Exception {
		var result = mockMvc.perform(get("/api/v1/catalog/access-benefits")).andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
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
	@WithMockUser(authorities = "access.manage")
	void generalAccessManagementCannotConfigurePhysicalInfrastructure() throws Exception {
		mockMvc.perform(put("/api/v1/access/physical-integration")
						.contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/access/devices")
						.contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "access.physical.manage")
	void physicalAccessManagersCanReachConfigurationEndpoints() throws Exception {
		var integration = mockMvc.perform(put("/api/v1/access/physical-integration")
				.contentType("application/json").content("{}"))
				.andReturn();
		var device = mockMvc.perform(post("/api/v1/access/devices")
				.contentType("application/json").content("{}"))
				.andReturn();

		assertThat(integration.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(device.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "access.manage")
	void generalAccessManagementCannotOpenAManualDevice() throws Exception {
		mockMvc.perform(post("/api/v1/access/commands/open")
						.contentType("application/json")
						.content("{\"deviceCode\":\"MAIN\",\"reason\":\"Test\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "access.manual.open")
	void dedicatedManualOpenPermissionCanReachTheCommand() throws Exception {
		var result = mockMvc.perform(post("/api/v1/access/commands/open")
				.contentType("application/json")
				.content("{\"deviceCode\":\"MAIN\",\"reason\":\"Test\"}"))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "platform.admin")
	void platformHomeCannotReachTenantManualOpenCommandWithoutSupportScope() throws Exception {
		mockMvc.perform(post("/api/v1/access/commands/open")
				.contentType("application/json")
				.content("{\"deviceCode\":\"MAIN\",\"reason\":\"Soporte autorizado\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void physicalConnectorApiUsesItsOwnAuthenticationInsteadOfJwt() throws Exception {
		mockMvc.perform(post("/api/v1/physical-connectors/heartbeat")
						.contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("CONNECTOR_AUTH_INVALID"));
	}

	@Test
	@WithMockUser(authorities = "client.read")
	void clientReadersCanInspectEffectiveAccessBenefitsWithoutAccessAdministrationPermission() throws Exception {
		var result = mockMvc.perform(get(
				"/api/v1/access/clients/99999999-9999-9999-9999-999999999951/effective-benefits"
		)).andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
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
	@WithMockUser(authorities = "selfservice.session.launch")
	void legacyTerminalSessionCreationIsNotAvailable() throws Exception {
		mockMvc.perform(post("/api/v1/self-service/terminals/UNKNOWN/sessions"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(authorities = "selfservice.session.launch")
	void authorizedReceptionCanReachKioskLaunchEndpoint() throws Exception {
		var result = mockMvc.perform(post("/api/v1/self-service/kiosk-sessions")).andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "platform.admin")
	void platformAdministratorCannotLaunchKioskSession() throws Exception {
		mockMvc.perform(post("/api/v1/self-service/kiosk-sessions"))
				.andExpect(status().isForbidden());
	}

	@Test
	void paymentProviderCallbacksAndWebhooksArePublicButStillValidateTheirPayload() throws Exception {
		var oauth = mockMvc.perform(get("/api/v1/payment-settings/mercado-pago/oauth/callback")).andReturn();
		var webhook = mockMvc.perform(post("/api/v1/payment-webhooks/stripe")
				.contentType("application/json").content("{}"))
				.andReturn();

		assertThat(oauth.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(webhook.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "payment.settings.read")
	void paymentProviderConnectionRequiresManagePermission() throws Exception {
		mockMvc.perform(post("/api/v1/payment-settings/stripe/connect"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "payment.settings.read")
	void mercadoPagoTerminalInventoryRequiresManagePermission() throws Exception {
		mockMvc.perform(get("/api/v1/payment-settings/mercado-pago/terminals"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "payment.manual.create")
	void mercadoPagoPointOrderRequiresSaleCreationPermission() throws Exception {
		mockMvc.perform(post("/api/v1/sales/payments/99999999-9999-9999-9999-999999999951/mercado-pago-order"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "automation.read")
	void automationManualExecutionRequiresExecutePermission() throws Exception {
		mockMvc.perform(post("/api/v1/automations/99999999-9999-9999-9999-999999999951/execute"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "automation.execute")
	void automationExecutePermissionCanReachManualExecutionEndpoint() throws Exception {
		var result = mockMvc.perform(post("/api/v1/automations/99999999-9999-9999-9999-999999999951/execute"))
				.andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "automation.read")
	void automationReadPermissionCanReachDeadLetterInbox() throws Exception {
		var result = mockMvc.perform(get("/api/v1/automations/dead-letters")).andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "automation.execute")
	void automationDeadLetterRecoveryRequiresManagePermission() throws Exception {
		mockMvc.perform(post(
				"/api/v1/automations/dead-letters/99999999-9999-9999-9999-999999999951/retry"
		).contentType("application/json").content("{\"source\":\"EVENT\",\"reason\":\"Reintentar\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "automation.manage")
	void schedulerControlRequiresDedicatedPermission() throws Exception {
		mockMvc.perform(post("/api/v1/automations/scheduler/pause")
				.contentType("application/json").content("{\"reason\":\"Mantenimiento\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "automation.scheduler.manage")
	void schedulerPermissionCanReachControlEndpoint() throws Exception {
		var result = mockMvc.perform(post("/api/v1/automations/scheduler/pause")
				.contentType("application/json").content("{\"reason\":\"Mantenimiento\"}"))
				.andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = "payment.settings.manage")
	void kioskSettingsRequireDedicatedOwnerPermission() throws Exception {
		mockMvc.perform(get("/api/v1/kiosk-settings/branches"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = "selfservice.settings.manage")
	void ownerCanReachLogicalKioskSettings() throws Exception {
		var result = mockMvc.perform(get("/api/v1/kiosk-settings/branches")).andReturn();
		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	@WithMockUser(authorities = {
			"organization.read", "branch.read", "payment.settings.read", "payment.settings.manage",
			"selfservice.settings.manage", "notification.settings.manage"
	})
	void managerPermissionBundleCanReachEveryOperationalSettingsEndpoint() throws Exception {
		var organization = mockMvc.perform(get("/api/v1/organizations/current")).andReturn();
		var branches = mockMvc.perform(get("/api/v1/organizations/current/branches")).andReturn();
		var payments = mockMvc.perform(get("/api/v1/payment-settings")).andReturn();
		var kiosk = mockMvc.perform(get("/api/v1/kiosk-settings/branches")).andReturn();
		var email = mockMvc.perform(get("/api/v1/notifications/email-configuration")).andReturn();

		assertThat(organization.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(branches.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(payments.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(kiosk.getResponse().getStatus()).isNotIn(401, 403);
		assertThat(email.getResponse().getStatus()).isNotIn(401, 403);
	}
}
