package com.gymos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.support.OperationalTestData;
import com.gymos.wellness.infrastructure.WellnessRepository;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateAppointmentData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantIsolationPostgresIntegrationTests {
	private static final String KIOSK_SETTINGS = """
			{"clientRegistration":false,"membershipEnrollment":true,"visitPackagePurchase":false,
			 "membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,
			 "wellnessBooking":false,"profileUpdate":true,"autopay":true}
			""";

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", postgres::getJdbcUrl);
		properties.add("spring.datasource.username", postgres::getUsername);
		properties.add("spring.datasource.password", postgres::getPassword);
		properties.add("spring.data.redis.host", redis::getHost);
		properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired MockMvc http;
	@Autowired JdbcClient jdbc;
	@Autowired JwtService jwt;
	@Autowired ObjectMapper json;
	@Autowired WellnessRepository wellness;
	@Autowired PasswordEncoder passwordEncoder;

	private OperationalTestData tenant;
	private OperationalTestData foreign;
	private UUID north;

	@BeforeEach
	void fixtures() {
		tenant = new OperationalTestData(jdbc);
		foreign = new OperationalTestData(jdbc);
		north = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO branch(id, organization_id, name, code, timezone, status, created_at, updated_at)
				VALUES (:id, :org, 'Norte', 'NORTE', 'America/Mexico_City', 'ACTIVE', now(), now())
				""").param("id", north).param("org", tenant.organizationId).update();
	}

	@Test
	void clientAndPackageRoutesIgnoreSpoofedContextAndRejectForeignIds() throws Exception {
		UUID home = tenant.client();
		UUID otherBranch = northClient();
		UUID otherTenant = foreign.client();
		String token = token(tenant.ownerId, tenant.branchId, Set.of("client.read", "sale.create"), "Recepcion");
		http.perform(auth(get("/api/v1/clients/{id}", home), token)).andExpect(status().isOk());
		for (UUID unavailable : Set.of(otherBranch, otherTenant, UUID.randomUUID())) {
			for (String suffix : new String[] {"", "/visit-package", "/visit-packages", "/memberships", "/benefits"}) {
				http.perform(auth(get("/api/v1/clients/{id}" + suffix, unavailable), token)
								.header("X-Organization-Id", foreign.organizationId)
								.header("X-Branch-Id", north).param("organizationId", foreign.organizationId.toString())
								.param("branchId", north.toString()))
						.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
			}
			http.perform(auth(post("/api/v1/visit-packages/quotes"), token).contentType("application/json")
							.content("""
									{"clientId":"%s","packagePlanId":"%s","organizationId":"%s","branchId":"%s"}
									""".formatted(unavailable, UUID.randomUUID(), foreign.organizationId, north)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
			for (var purchase : java.util.Map.of("/api/v1/visit-packages/purchases", "packagePlanId",
					"/api/v1/membership-enrollments", "planId").entrySet()) {
				http.perform(auth(post(purchase.getKey()), token).contentType("application/json")
								.content("""
										{"clientId":"%s","%s":"%s","method":"CASH","validateNow":true,"idempotencyKey":"qa"}
										""".formatted(unavailable, purchase.getValue(), UUID.randomUUID())))
						.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
			}
		}
		http.perform(auth(get("/api/v1/clients/{id}/visit-package", home),
				token(tenant.ownerId, tenant.branchId, Set.of("sale.create"), "Recepcion"))).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/clients/{id}/visit-packages", home),
				token(tenant.ownerId, tenant.branchId, Set.of("client.create"), "Recepcion"))).andExpect(status().isForbidden());
	}

	@Test
	void sharedMembershipAllowsTheClientInAnotherBranchOnlyWhileCovered() throws Exception {
		UUID client = northClient();
		UUID plan = tenant.plan("Multisede", 30);
		jdbc.sql("UPDATE membership_plan SET multi_branch_access=true WHERE id=:id").param("id", plan).update();
		UUID membership = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO membership(id, organization_id, branch_id, client_id, plan_id, sale_id, status, activated_at,
				                       starts_on, ends_on, grace_ends_on, created_at, updated_at)
				VALUES (:id, :org, :branch, :client, :plan, :sale, 'ACTIVE', now(),
				        CURRENT_DATE - 3, CURRENT_DATE + 5, CURRENT_DATE + 8, now(), now())
				""").param("id", membership).param("org", tenant.organizationId).param("branch", north)
				.param("client", client).param("plan", plan).param("sale", sale(tenant.organizationId, north, 100)).update();
		String token = token(tenant.ownerId, tenant.branchId, Set.of("client.read", "membership.read"), "Recepcion");
		http.perform(auth(get("/api/v1/clients/{id}", client), token)).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/memberships/{id}", membership), token)).andExpect(status().isOk());
		String outside = jwt.accessToken(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
				"fixture", "Fixture", Set.of("membership.read", "sale.create"), Set.of("Recepcion"), false));
		http.perform(auth(get("/api/v1/memberships/{id}", membership), outside)).andExpect(status().isBadRequest());
		http.perform(auth(post("/api/v1/memberships/{id}/renew", membership), outside)
						.contentType("application/json").content("{\"method\":\"CASH\",\"validateNow\":true}"))
				.andExpect(status().isBadRequest());
		UUID benefit = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO benefit(id, organization_id, branch_id, code, name, benefit_type,
				                    discount_type, status, access_scope, created_at, updated_at)
				VALUES (:id,:org,:branch,'QA-ACCESS','Acceso multisede','ACCESS','NONE','ACTIVE','PLAN_COVERAGE',now(),now())
				""").param("id", benefit).param("org", tenant.organizationId).param("branch", north).update();
		jdbc.sql("INSERT INTO benefit_membership_plan(organization_id, benefit_id, membership_plan_id) VALUES (:org,:benefit,:plan)")
				.param("org", tenant.organizationId).param("benefit", benefit).param("plan", plan).update();
		String reception = token(tenant.ownerId, tenant.branchId, Set.of("access.check-in"), "Recepcion");
		String checkIn = "/api/v1/access/manual-check-ins";
		UUID key = UUID.randomUUID();
		String request = "{\"branchId\":\"%s\",\"clientId\":\"%s\",\"benefitId\":\"%s\"}"
				.formatted(tenant.branchId, client, benefit);
		for (int attempt = 0; attempt < 2; attempt++) {
			http.perform(auth(post(checkIn), reception).header("Idempotency-Key", key)
							.contentType("application/json").content(request))
					.andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("GRANTED"))
					.andExpect(jsonPath("$.membershipId").value(membership.toString()));
		}
		http.perform(auth(get(checkIn), reception)).andExpect(jsonPath("$.entries.length()").value(1));
		jdbc.sql("UPDATE membership SET ends_on=CURRENT_DATE-2, grace_ends_on=CURRENT_DATE-2 WHERE id=:id")
				.param("id", membership).update();
		http.perform(auth(get("/api/v1/clients/{id}", client), token))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
	}

	@Test
	void benefitsAndKioskSettingsRejectUnassignedBranchEvenInsideTheTenant() throws Exception {
		String token = token(tenant.ownerId, tenant.branchId, Set.of("benefit.manage", "selfservice.settings.manage"), "Gerente");
		String definition = """
				{"branchId":"%s","code":"STUDENT","name":"Estudiante","benefitType":"STUDENT",
				 "discountType":"PERCENT","discountPercent":10,"status":"ACTIVE",
				 "requiresEvidence":false,"combinable":false,"priority":10}
				""";
		for (UUID unavailable : Set.of(north, foreign.branchId)) {
			http.perform(auth(post("/api/v1/benefits"), token).contentType("application/json")
					.content(definition.formatted(unavailable))).andExpect(status().isForbidden());
			http.perform(auth(put("/api/v1/kiosk-settings/branches/{id}", unavailable), token)
							.contentType("application/json").content(KIOSK_SETTINGS))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("KIOSK_BRANCH_NOT_FOUND"));
		}
		http.perform(auth(get("/api/v1/kiosk-settings/branches"), token))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].branchId").value(tenant.branchId.toString()));
		assignBranch(tenant.ownerId, north);
		http.perform(auth(put("/api/v1/kiosk-settings/branches/{id}", north), token)
						.contentType("application/json").content(KIOSK_SETTINGS))
				.andExpect(status().isOk());
		http.perform(auth(post("/api/v1/benefits"), token).contentType("application/json")
				.content(definition.formatted(north))).andExpect(status().isForbidden());
	}

	@Test
	void receiptReferencesAreServerOwnedAndReportsIgnoreForeignSalesAndClients() throws Exception {
		UUID localSale = sale(tenant.organizationId, tenant.branchId, 100);
		UUID foreignSale = sale(foreign.organizationId, foreign.branchId, 900);
		UUID northSale = sale(tenant.organizationId, north, 800);
		tenant.client();
		foreign.client();
		northClient();
		String token = token(tenant.ownerId, tenant.branchId,
				Set.of("sale.read", "payment.manual.create", "report.owner.read"), "Gerente");
		String receipt = "receipts/" + foreign.organizationId + "/" + foreignSale + "/" + UUID.randomUUID() + "/receipt.pdf";
		for (UUID unavailable : Set.of(northSale, foreignSale)) {
			http.perform(auth(get("/api/v1/sales/{id}", unavailable), token)).andExpect(status().isBadRequest());
			http.perform(auth(post("/api/v1/sales/{id}/payments", unavailable), token).contentType("application/json")
					.content("{\"method\":\"TRANSFER\",\"amountCents\":100,\"validateNow\":false}")).andExpect(status().isBadRequest());
		}
		jdbc.sql("UPDATE sale SET status='PENDING_PAYMENT', paid_cents=0, balance_cents=total_cents WHERE id=:id")
				.param("id", localSale).update();
		http.perform(auth(post("/api/v1/sales/{id}/payments", localSale), token).contentType("application/json")
						.content("{\"method\":\"TRANSFER\",\"amountCents\":100,\"validateNow\":false,\"receiptObjectKey\":\"%s\"}".formatted(receipt)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_UPLOAD_REQUIRED"));
		assertThat(jdbc.sql("SELECT count(*) FROM payment WHERE sale_id=:sale").param("sale", localSale)
				.query(Integer.class).single()).isZero();
		jdbc.sql("UPDATE sale SET status='PAID', paid_cents=total_cents, balance_cents=0 WHERE id=:id")
				.param("id", localSale).update();
		var reportDate = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
		http.perform(auth(get("/api/v1/reports/owner-dashboard"), token).param("branchId", north.toString())
						.param("organizationId", foreign.organizationId.toString())
						.param("fromDate", reportDate.minusDays(1).toString()).param("toDate", reportDate.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.saleCount").value(1))
				.andExpect(jsonPath("$.paidRevenueCents").value(100)).andExpect(jsonPath("$.newClients").value(1));
		UUID localPayment = payment(tenant.organizationId, tenant.branchId, localSale);
		jdbc.sql("UPDATE payment SET receipt_object_key=:key WHERE id=:id")
				.param("key", receipt).param("id", localPayment).update();
		http.perform(auth(get("/api/v1/sales/{id}", localSale), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.payments[0].receiptObjectKey").doesNotExist());
		String ownReceipt = "receipts/" + tenant.organizationId + "/" + localSale + "/" + localPayment + "/qa.pdf";
		jdbc.sql("UPDATE payment SET receipt_object_key=:key WHERE id=:id")
				.param("key", ownReceipt).param("id", localPayment).update();
		http.perform(auth(get("/api/v1/sales/{id}", localSale), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.payments[0].receiptObjectKey").value(ownReceipt));
	}

	@Test
	void selectingAssignedBranchRetainsLegitimateAccessAndRevocationInvalidatesTheToken() throws Exception {
		UUID client = northClient();
		String token = token(tenant.ownerId, tenant.branchId, Set.of("branch.read", "client.read"), "Gerente");
		for (UUID unavailable : Set.of(north, foreign.branchId)) {
			http.perform(auth(post("/api/v1/auth/select-branch"), token).contentType("application/json")
							.content("{\"branchId\":\"%s\"}".formatted(unavailable)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BRANCH_ACCESS_DENIED"));
		}
		assignBranch(tenant.ownerId, north);
		grant(tenant.ownerId, "client.read");
		var selected = http.perform(auth(post("/api/v1/auth/select-branch"), token).contentType("application/json")
						.content("{\"branchId\":\"%s\"}".formatted(north)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.branchId").value(north.toString())).andReturn();
		String northToken = json.readTree(selected.getResponse().getContentAsByteArray()).path("accessToken").asText();
		http.perform(auth(get("/api/v1/clients/{id}", client), northToken)).andExpect(status().isOk());
		assertThat(auditCount(tenant.organizationId, north, "BRANCH_SELECTED", north)).isEqualTo(1);
		jdbc.sql("DELETE FROM user_branch_access WHERE user_id=:user AND branch_id=:branch")
				.param("user", tenant.ownerId).param("branch", north).update();
		http.perform(auth(get("/api/v1/clients/{id}", client), northToken)).andExpect(status().isUnauthorized());
		http.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isOk());
		jdbc.sql("UPDATE organization SET status='SUSPENDED' WHERE id=:id").param("id", tenant.organizationId).update();
		http.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
	}

	@Test
	void accountWideChangesNeedEveryTargetBranchAndAreAuditedOnceAuthorized() throws Exception {
		UUID manager = tenant.user("Gerente", "test-hash");
		UUID shared = tenant.user("Recepcion", "test-hash");
		assignBranch(shared, north);
		String managerToken = token(manager, tenant.branchId, Set.of("user.read", "user.manage"), "Gerente");
		String readerToken = token(manager, tenant.branchId, Set.of("user.read"), "Gerente");
		String statusBody = "{\"status\":\"SUSPENDED\"}";
		http.perform(auth(get("/api/v1/users/internal"), managerToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(shared.toString()));
		http.perform(auth(patch("/api/v1/users/internal/{id}/status", shared), readerToken)
						.contentType("application/json").content(statusBody)).andExpect(status().isForbidden());
		http.perform(auth(patch("/api/v1/users/internal/{id}/status", shared), managerToken)
						.contentType("application/json").content(statusBody)).andExpect(status().isForbidden());
		http.perform(auth(patch("/api/v1/users/internal/{id}/status", foreign.ownerId), managerToken)
						.contentType("application/json").content(statusBody))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "INTERNAL_USER_STATUS_UPDATED", shared)).isZero();
		assignBranch(manager, north);
		http.perform(auth(patch("/api/v1/users/internal/{id}/status", shared), managerToken)
						.contentType("application/json").content(statusBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "INTERNAL_USER_STATUS_UPDATED", shared)).isEqualTo(1);
	}

	@Test
	void resourceReadUpdateAndDeleteUseTheSelectedBranch() throws Exception {
		UUID resource = resource(tenant.organizationId, north);
		UUID otherTenant = resource(foreign.organizationId, foreign.branchId);
		String token = token(tenant.ownerId, tenant.branchId, Set.of("resource.read", "resource.manage"), "Gerente");
		String resourceBody = """
				{"name":"Cambio","resourceType":"ROOM","capacity":1,"slotMinutes":60,"opensAt":"08:00","closesAt":"21:00"}
				""";
		for (UUID unavailable : Set.of(resource, otherTenant)) {
			http.perform(auth(get("/api/v1/resources/{id}/availability", unavailable), token)).andExpect(status().isBadRequest());
			http.perform(auth(put("/api/v1/resources/{id}", unavailable), token).contentType("application/json")
					.content(resourceBody)).andExpect(status().isBadRequest());
			http.perform(auth(delete("/api/v1/resources/{id}", unavailable), token)).andExpect(status().isBadRequest());
		}
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "RESOURCE_DELETED", resource)).isZero();
		assignBranch(tenant.ownerId, north);
		String northToken = token(tenant.ownerId, north, Set.of("resource.manage"), "Gerente");
		http.perform(auth(put("/api/v1/resources/{id}", resource), northToken).contentType("application/json")
				.content(resourceBody)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cambio"));
		http.perform(auth(delete("/api/v1/resources/{id}", resource), northToken)).andExpect(status().is2xxSuccessful());
		assertThat(auditCount(tenant.organizationId, north, "RESOURCE_DELETED", resource)).isEqualTo(1);
	}

	@Test
	void inventoryCannotTransferAcrossTenantsButCanShipAndReceiveWithinOne() throws Exception {
		UUID product = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO catalog_product(id, organization_id, sku, name, product_type, price_cents, currency,
				                            status, inventory_tracked, created_at, updated_at)
				VALUES (:id, :org, 'QA', 'Producto', 'PHYSICAL', 100, 'MXN', 'ACTIVE', true, now(), now())
				""").param("id", product).param("org", tenant.organizationId).update();
		jdbc.sql("""
				INSERT INTO catalog_product_branch(organization_id, branch_id, product_id, created_at)
				VALUES (:org, :origin, :product, now()), (:org, :destination, :product, now())
				""").param("org", tenant.organizationId).param("origin", tenant.branchId)
				.param("destination", north).param("product", product).update();
		jdbc.sql("""
				INSERT INTO inventory_stock(id, organization_id, branch_id, product_id, quantity_on_hand, created_at, updated_at)
				VALUES (:id, :org, :branch, :product, 3, now(), now())
				""").param("id", UUID.randomUUID()).param("org", tenant.organizationId)
				.param("branch", tenant.branchId).param("product", product).update();
		String token = token(tenant.ownerId, tenant.branchId, Set.of("inventory.read", "inventory.manage"), "Gerente");
		String body = "{\"destinationBranchId\":\"%s\",\"items\":[{\"productId\":\"%s\",\"requestedQuantity\":1}]}";
		http.perform(auth(post("/api/v1/inventory/transfers"), token).contentType("application/json")
						.content(body.formatted(foreign.branchId, product)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY_TRANSFER_DESTINATION_NOT_FOUND"));
		assertThat(jdbc.sql("SELECT count(*) FROM inventory_transfer WHERE organization_id=:org")
				.param("org", tenant.organizationId).query(Integer.class).single()).isZero();
		var response = http.perform(auth(post("/api/v1/inventory/transfers"), token).contentType("application/json")
						.content(body.formatted(north, product)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.destinationBranchId").value(north.toString())).andReturn();
		String transferId = json.readTree(response.getResponse().getContentAsByteArray()).path("id").asText();
		String outside = jwt.accessToken(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
				"fixture", "Fixture", Set.of("inventory.read", "inventory.manage"), Set.of("Gerente"), false));
		http.perform(auth(get("/api/v1/inventory/transfers/{id}", transferId), outside)).andExpect(status().isBadRequest());
		http.perform(auth(post("/api/v1/inventory/transfers/{id}/ship", transferId), outside)).andExpect(status().isBadRequest());
		http.perform(auth(post("/api/v1/inventory/transfers/{id}/ship", transferId), token))
				.andExpect(status().isOk());
		http.perform(auth(post("/api/v1/inventory/transfers/{id}/receive", transferId), token))
				.andExpect(status().isBadRequest());
		assignBranch(tenant.ownerId, north);
		http.perform(auth(post("/api/v1/inventory/transfers/{id}/receive", transferId),
						token(tenant.ownerId, north, Set.of("inventory.manage"), "Gerente")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));
	}

	@Test
	void wellnessRejectsForeignClientEvenAtThePersistenceBoundary() throws Exception {
		UUID professional = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO professional(id, organization_id, branch_id, display_name, professional_type, status, created_at, updated_at)
				VALUES (:id, :org, :branch, 'Fisio QA', 'PHYSIOTHERAPIST', 'ACTIVE', now(), now())
				""").param("id", professional).param("org", tenant.organizationId).param("branch", tenant.branchId).update();
		OffsetDateTime start = OffsetDateTime.now().plusDays(1);
		UUID otherTenant = foreign.client();
		UUID otherBranch = northClient();
		String token = token(tenant.ownerId, tenant.branchId, Set.of("professional.manage", "professional.read"), "Gerente");
		String body = "{\"professionalId\":\"%s\",\"clientId\":\"%s\",\"startsAt\":\"%s\"}";
		for (UUID unavailable : Set.of(otherTenant, otherBranch)) {
			http.perform(auth(post("/api/v1/wellness/appointments"), token).contentType("application/json")
							.content(body.formatted(professional, unavailable, start)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
		}
		assertThatThrownBy(() -> wellness.createAppointment(tenant.organizationId, tenant.branchId, tenant.ownerId,
				new CreateAppointmentData(professional, null, otherTenant, start, start.plusHours(1), "REQUESTED", null, null, null)))
				.isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("CLIENT_NOT_FOUND"));
		http.perform(auth(get("/api/v1/wellness/appointments"), token)).andExpect(content().json("[]"));
		http.perform(auth(post("/api/v1/wellness/appointments"), token).contentType("application/json")
				.content(body.formatted(professional, tenant.client(), start))).andExpect(status().isOk());
	}

	@Test
	void checkInRejectsForgedBranchAndForeignClientWithoutCreatingHistory() throws Exception {
		String token = token(tenant.ownerId, tenant.branchId, Set.of("access.check-in"), "Recepcion");
		String body = "{\"branchId\":\"%s\",\"clientId\":\"%s\"}";
		http.perform(auth(post("/api/v1/access/manual-check-ins"), token).header("Idempotency-Key", UUID.randomUUID())
						.contentType("application/json").content(body.formatted(north, tenant.client())))
				.andExpect(status().isForbidden());
		http.perform(auth(post("/api/v1/access/manual-check-ins"), token).header("Idempotency-Key", UUID.randomUUID())
						.contentType("application/json").content(body.formatted(tenant.branchId, foreign.client())))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHECK_IN_CLIENT_NOT_FOUND"));
		http.perform(auth(get("/api/v1/access/manual-check-ins"), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entries").isEmpty());
	}

	@Test
	void kioskSessionRejectsAnotherSessionsTokenAndIsRevokedWhenItsBranchCloses() throws Exception {
		String launchPath = "/api/v1/self-service/kiosk-sessions";
		String launchPermission = "selfservice.session.launch";
		http.perform(auth(post(launchPath), token(tenant.ownerId, tenant.branchId, Set.of(launchPermission), "Gerente")))
				.andExpect(status().isForbidden());
		String reception = token(tenant.ownerId, tenant.branchId, Set.of(launchPermission), "Recepcion");
		var foreignReception = new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
				"fixture", "Fixture", Set.of(launchPermission), Set.of("Recepcion"), false);
		var localSession = json.readTree(http.perform(auth(post(launchPath), reception)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray());
		var otherSession = json.readTree(http.perform(auth(post(launchPath), jwt.accessToken(foreignReception))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray());
		String summary = "/api/v1/self-service/sessions/{id}/summary";
		String localId = localSession.path("sessionId").asText();
		String kioskToken = localSession.path("sessionToken").asText();
		String header = "X-Self-Service-Session-Token";
		for (String unavailable : Set.of(otherSession.path("sessionId").asText(), UUID.randomUUID().toString())) {
			http.perform(get(summary, unavailable).header(header, kioskToken))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_SERVICE_SESSION_INVALID"));
		}
		http.perform(get(summary, localId).header(header, kioskToken))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_SERVICE_SESSION_NOT_VERIFIED"));
		jdbc.sql("UPDATE branch SET status='INACTIVE' WHERE id=:id").param("id", tenant.branchId).update();
		http.perform(get(summary, localId).header(header, kioskToken))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_SERVICE_SESSION_INVALID"));
	}

	@Test
	void notificationTargetsAndRetriesAreScopedToTheBranch() throws Exception {
		UUID local = notification(tenant.organizationId, tenant.branchId);
		UUID sameTenantOtherBranch = notification(tenant.organizationId, north);
		UUID foreignNotification = notification(foreign.organizationId, foreign.branchId);
		String token = token(tenant.ownerId, tenant.branchId, Set.of("notification.manage"), "Gerente");
		for (UUID unavailable : Set.of(sameTenantOtherBranch, foreignNotification)) {
			http.perform(auth(post("/api/v1/notifications/{id}/retry", unavailable), token))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_RETRYABLE"));
			assertThat(jdbc.sql("SELECT status FROM notification_log WHERE id=:id").param("id", unavailable)
					.query(String.class).single()).isEqualTo("DEAD_LETTER");
		}
		for (UUID unavailable : Set.of(northClient(), foreign.client())) {
			http.perform(auth(post("/api/v1/notifications"), token).contentType("application/json")
							.content("""
									{"channel":"IN_APP","body":"Prueba","recipientType":"CLIENT","recipientId":"%s"}
									""".formatted(unavailable)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
		}
		http.perform(auth(post("/api/v1/notifications/{id}/retry", local), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QUEUED"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "NOTIFICATION_EMAIL_RETRIED", local)).isEqualTo(1);
	}

	@Test
	void rolesFromAnotherTenantCannotBeListedAssignedOrInheritedAtTokenIssuance() throws Exception {
		UUID staff = tenant.user("Recepcion", "test-hash");
		UUID outsideUser = foreign.user("Rol de otro gimnasio", "test-hash");
		UUID outsideRole = jdbc.sql("SELECT role_id FROM user_role WHERE user_id=:id")
				.param("id", outsideUser).query(UUID.class).single();
		jdbc.sql("UPDATE role SET system_role=true WHERE id=:id").param("id", outsideRole).update();
		grant(outsideUser, "user.manage");

		String ownerToken = token(tenant.ownerId, tenant.branchId, Set.of("user.read", "user.manage"), "Dueno del gimnasio");
		http.perform(auth(get("/api/v1/users/roles"), ownerToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '%s')]".formatted(outsideRole)).isEmpty());
		http.perform(auth(patch("/api/v1/users/internal/{id}/role", staff), ownerToken)
						.contentType("application/json").content("{\"roleId\":\"%s\"}".formatted(outsideRole)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ROLE_NOT_AVAILABLE"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "INTERNAL_USER_ROLE_UPDATED", staff)).isZero();
		grant(staff, "branch.read");
		jdbc.sql("INSERT INTO user_role(user_id, role_id) VALUES (:user,:role)")
				.param("user", staff).param("role", outsideRole).update();
		String staffToken = token(staff, tenant.branchId, Set.of("branch.read"), "Recepcion");
		var selected = http.perform(auth(post("/api/v1/auth/select-branch"), staffToken)
						.contentType("application/json").content("{\"branchId\":\"%s\"}".formatted(tenant.branchId)))
				.andExpect(status().isOk()).andReturn();
		String issued = json.readTree(selected.getResponse().getContentAsByteArray()).path("accessToken").asText();
		assertThat(jwt.parseAccessToken(issued).orElseThrow().permissions()).containsExactly("branch.read");
		assertThat(jwt.parseAccessToken(issued).orElseThrow().roles()).containsExactly("Recepcion");
		UUID receptionRole = jdbc.sql("""
				SELECT id FROM role WHERE name='Recepcion' AND organization_id IS NULL AND system_role
				  AND account_scope='TENANT' AND deleted_at IS NULL
				""").query(UUID.class).single();
		http.perform(auth(patch("/api/v1/users/internal/{id}/role", staff), ownerToken)
						.contentType("application/json").content("{\"roleId\":\"%s\"}".formatted(receptionRole)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("Recepcion"));
		http.perform(auth(get("/api/v1/auth/me"), issued)).andExpect(status().isUnauthorized());
	}

	@Test
	void branchWritesAndOrganizationStatusUseDifferentAdministrativeScopes() throws Exception {
		String token = token(tenant.ownerId, tenant.branchId, Set.of("branch.read", "branch.update"), "Dueno del gimnasio");
		String branchBody = """
				{"name":"Nueva sede","code":"NORTE-2","timezone":"America/Mexico_City","phone":"+525512345678"}
				""";
		for (UUID outside : Set.of(north, foreign.branchId)) {
			http.perform(auth(put("/api/v1/organizations/current/branches/{id}", outside), token)
							.contentType("application/json").content(branchBody))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BRANCH_ACCESS_DENIED"));
			assertThat(auditCount(tenant.organizationId, tenant.branchId, "BRANCH_UPDATED", outside)).isZero();
		}
		http.perform(auth(patch("/api/v1/organizations/{id}/status", foreign.organizationId), token)
				.contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isForbidden());
		assignBranch(tenant.ownerId, north);
		http.perform(auth(put("/api/v1/organizations/current/branches/{id}", north), token)
						.contentType("application/json").content(branchBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nueva sede"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "BRANCH_UPDATED", north)).isEqualTo(1);
	}

	@Test
	void bookingPaymentRequiresTheSameClientBranchAndTenant() throws Exception {
		UUID client = tenant.client();
		UUID booking = booking(tenant.organizationId, tenant.branchId, client);
		UUID northBooking = booking(tenant.organizationId, north, northClient());
		UUID foreignBooking = booking(foreign.organizationId, foreign.branchId, foreign.client());
		UUID ownSale = saleForClient(tenant.organizationId, tenant.branchId, client);
		UUID otherClientSale = saleForClient(tenant.organizationId, tenant.branchId, tenant.client());
		UUID northSale = saleForClient(tenant.organizationId, north, client);
		UUID foreignSale = saleForClient(foreign.organizationId, foreign.branchId, foreign.client());
		String token = token(tenant.ownerId, tenant.branchId, Set.of("booking.read", "booking.payment.manage"), "Gerente");
		String path = "/api/v1/bookings/{id}/payments";
		String body = "{\"saleId\":\"%s\",\"status\":\"PENDING\"}";
		for (UUID outside : Set.of(otherClientSale, northSale, foreignSale, UUID.randomUUID())) {
			http.perform(auth(post(path, booking), token).contentType("application/json").content(body.formatted(outside)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
		}
		for (UUID outside : Set.of(northBooking, foreignBooking)) {
			http.perform(auth(post(path, outside), token).contentType("application/json").content(body.formatted(ownSale)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
		}
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "BOOKING_PAYMENT_LINKED", booking)).isZero();
		jdbc.sql("""
				INSERT INTO booking_payment(id, organization_id, branch_id, booking_id, sale_id, status, created_at, updated_at)
				VALUES (:id,:org,:branch,:booking,:sale,'PENDING',now(),now())
				""").param("id", UUID.randomUUID()).param("org", tenant.organizationId).param("branch", tenant.branchId)
				.param("booking", booking).param("sale", foreignSale).update();
		http.perform(auth(get("/api/v1/bookings/{id}", booking), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.saleId").doesNotExist())
				.andExpect(jsonPath("$.saleNumber").doesNotExist());
		http.perform(auth(post(path, booking), token).contentType("application/json").content(body.formatted(ownSale)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.saleId").value(ownSale.toString()));
	}

	@Test
	void restrictedEmailPreferencesRequireAVisibleClient() throws Exception {
		String path = "/api/v1/notifications/email-preferences";
		String local = email(tenant.client());
		String northEmail = email(northClient());
		String foreignEmail = email(foreign.client());
		String reception = token(tenant.ownerId, tenant.branchId, Set.of("client.update"), "Recepcion");
		String body = "{\"recipient\":\"%s\",\"emailEnabled\":false,\"marketingEnabled\":false}";
		for (String outside : Set.of(northEmail, foreignEmail, "unknown@example.invalid")) {
			http.perform(auth(get(path), reception).param("recipient", outside)).andExpect(status().isForbidden());
			http.perform(auth(put(path), reception).contentType("application/json").content(body.formatted(outside)))
					.andExpect(status().isForbidden());
		}
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "NOTIFICATION_EMAIL_PREFERENCE_UPDATED",
				tenant.organizationId)).isZero();
		http.perform(auth(put(path), reception).contentType("application/json").content(body.formatted(local.toUpperCase(java.util.Locale.ROOT))))
				.andExpect(status().is2xxSuccessful());
		http.perform(auth(get(path), reception).param("recipient", local))
				.andExpect(status().isOk()).andExpect(jsonPath("$.emailEnabled").value(false));
		String manager = token(tenant.ownerId, tenant.branchId, Set.of("notification.manage"), "Gerente");
		http.perform(auth(put(path), manager).contentType("application/json").content(body.formatted(northEmail)))
				.andExpect(status().is2xxSuccessful());
	}

	@Test
	void foreignPaymentCannotBeValidatedRejectedAttachedOrRefunded() throws Exception {
		UUID localSale = sale(tenant.organizationId, tenant.branchId, 100);
		UUID northSale = sale(tenant.organizationId, north, 100);
		UUID foreignSale = sale(foreign.organizationId, foreign.branchId, 100);
		UUID northPayment = payment(tenant.organizationId, north, northSale);
		UUID foreignPayment = payment(foreign.organizationId, foreign.branchId, foreignSale);
		String token = token(tenant.ownerId, tenant.branchId,
				Set.of("payment.manual.create", "payment.manual.validate", "payment.manual.reject",
						"payment.receipt.upload", "sale.refund", "sale.create"), "Gerente");
		var file = new MockMultipartFile("file", "qa.pdf", "application/pdf", new byte[] {1});
		for (UUID outside : Set.of(northPayment, foreignPayment, UUID.randomUUID())) {
			http.perform(auth(post("/api/v1/sales/payments/{id}/validate", outside), token))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
			http.perform(auth(post("/api/v1/sales/payments/{id}/reject", outside), token)
							.contentType("application/json").content("{\"reason\":\"Prueba ajena\"}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
			http.perform(multipart("/api/v1/sales/payments/{id}/receipt", outside).file(file)
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
			http.perform(auth(post("/api/v1/sales/payments/{id}/mercado-pago-order", outside), token))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
			http.perform(auth(post("/api/v1/sales/{id}/refunds", localSale), token).contentType("application/json")
							.content("{\"paymentId\":\"%s\",\"amountCents\":10,\"reason\":\"Prueba ajena\"}".formatted(outside)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
		}
		for (UUID outside : Set.of(northSale, foreignSale, UUID.randomUUID())) {
			http.perform(auth(post("/api/v1/sales/{id}/payments", outside), token).contentType("application/json")
							.content("{\"method\":\"CASH\",\"amountCents\":10000}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
			http.perform(auth(post("/api/v1/sales/{id}/refunds", outside), token).contentType("application/json")
							.content("{\"amountCents\":10,\"reason\":\"Prueba ajena\"}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
		}
		for (UUID outside : Set.of(northPayment, foreignPayment)) {
			assertThat(jdbc.sql("SELECT status FROM payment WHERE id=:id AND receipt_object_key IS NULL")
					.param("id", outside).query(String.class).single()).isEqualTo("PENDING_VALIDATION");
		}
		assertThat(jdbc.sql("SELECT count(*) FROM sale_refund WHERE sale_id=:id")
				.param("id", localSale).query(Integer.class).single()).isZero();
	}

	@Test
	void manualCheckInHistoryAndReversalsUseTenantBranchAndEffectivePermission() throws Exception {
		UUID local = manualGrant(tenant, tenant.branchId, tenant.client());
		UUID northGrant = manualGrant(tenant, north, northClient());
		UUID foreignGrant = manualGrant(foreign, foreign.branchId, foreign.client());
		String operator = token(tenant.ownerId, tenant.branchId, Set.of("access.check-in"), "Recepcion");
		String manager = token(tenant.ownerId, tenant.branchId, Set.of("access.check-in", "access.check-in.reverse"), "Gerente");
		String path = "/api/v1/access/manual-check-ins";
		String reversal = "{\"branchId\":\"%s\",\"reason\":\"Entrada de prueba\"}";
		http.perform(auth(get(path), operator)).andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(1)).andExpect(jsonPath("$.entries[0].id").value(local.toString()));
		http.perform(auth(post(path + "/{id}/reversal", local), operator).contentType("application/json")
				.content(reversal.formatted(tenant.branchId))).andExpect(status().isForbidden());
		http.perform(auth(post(path + "/{id}/reversal", northGrant), manager).contentType("application/json")
				.content(reversal.formatted(north))).andExpect(status().isForbidden());
		for (UUID outside : Set.of(northGrant, foreignGrant, UUID.randomUUID())) {
			http.perform(auth(post(path + "/{id}/reversal", outside), manager).contentType("application/json")
							.content(reversal.formatted(tenant.branchId)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));
		}
		http.perform(auth(post(path + "/{id}/reversal", local), manager).contentType("application/json")
						.content(reversal.formatted(tenant.branchId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.reversal.reason").value("Entrada de prueba"));
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "MANUAL_CHECK_IN_REVERSED", local)).isEqualTo(1);
	}

	@Test
	void seededTenantRolesCanAuthenticateAndReachTheirLegitimateRoutes() throws Exception {
		var routes = java.util.Map.of(
				"Dueno del gimnasio", "/api/v1/organizations/current",
				"Gerente", "/api/v1/inventory",
				"Recepcion", "/api/v1/access/manual-check-ins",
				"Coach", "/api/v1/workouts");
		String password = "FixturePassword-29!";
		UUID coach = tenant.coach();
		UUID coachUser = jdbc.sql("SELECT user_account_id FROM coach WHERE id=:id")
				.param("id", coach).query(UUID.class).single();
		for (var role : routes.entrySet()) {
			UUID user = switch (role.getKey()) {
				case "Dueno del gimnasio" -> tenant.ownerId;
				case "Coach" -> coachUser;
				default -> tenant.user(role.getKey(), "test-hash");
			};
			jdbc.sql("UPDATE user_account SET password_hash=:hash WHERE id=:id")
					.param("id", user).param("hash", passwordEncoder.encode(password)).update();
			jdbc.sql("DELETE FROM user_role WHERE user_id=:id").param("id", user).update();
			assignSystemRole(user, role.getKey(), "TENANT");
			var loggedIn = http.perform(post("/api/v1/auth/login").contentType("application/json")
							.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, password)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.user.branchId").value(tenant.branchId.toString())).andReturn();
			String token = json.readTree(loggedIn.getResponse().getContentAsByteArray()).path("accessToken").asText();
			http.perform(auth(get(role.getValue()), token)).andExpect(status().isOk());
			http.perform(auth(get("/api/v1/platform/customers"), token)).andExpect(status().isForbidden());
		}
	}

	@Test
	void supportCanOnlyReadSelectedTenantAndCannotReuseTenantOperationsAsPlatformAdmin() throws Exception {
		UUID platform = UUID.randomUUID();
		UUID person = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO person(id, first_name, last_name, status, account_scope, created_at, updated_at)
				VALUES (:id, 'Soporte', 'QA', 'ACTIVE', 'PLATFORM', now(), now())
				""").param("id", person).update();
		jdbc.sql("""
				INSERT INTO user_account(id, person_id, username, password_hash, status, account_scope, created_at, updated_at)
				VALUES (:id, :person, :username, 'test-hash', 'ACTIVE', 'PLATFORM', now(), now())
				""").param("id", platform).param("person", person).param("username", platform.toString()).update();
		assignSystemRole(platform, "Administrador GymOS", "PLATFORM");
		var admin = new GymOsPrincipal(platform, AccountScope.PLATFORM, null, null,
				"support", "Soporte QA", Set.of("platform.admin"), Set.of("Administrador GymOS"), false, null);
		String adminToken = jwt.accessToken(admin);
		http.perform(auth(get("/api/v1/platform/customers"), adminToken)).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/clients/{id}", tenant.client()), adminToken)).andExpect(status().isForbidden());
		var selected = http.perform(auth(post("/api/v1/auth/select-organization"), adminToken)
						.contentType("application/json").content("""
								{"organizationId":"%s","reason":"Validación del contexto temporal"}
								""".formatted(tenant.organizationId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.supportMode").value(true)).andReturn();
		String token = json.readTree(selected.getResponse().getContentAsByteArray()).path("accessToken").asText();
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "PLATFORM_SUPPORT_STARTED", tenant.organizationId)).isEqualTo(1);
		http.perform(auth(get("/api/v1/organizations/{id}/branches", tenant.organizationId), token)).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/organizations/{id}/branches", foreign.organizationId), token)).andExpect(status().isForbidden());
		http.perform(auth(get("/api/v1/clients/{id}", tenant.client()), token)).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/clients/{id}", foreign.client()), token)).andExpect(status().isBadRequest());
		http.perform(auth(get("/api/v1/access/manual-check-ins"), token)).andExpect(status().isForbidden());
		http.perform(auth(get("/api/v1/platform/customers"), token)).andExpect(status().isForbidden());
		http.perform(auth(patch("/api/v1/organizations/{id}/status", foreign.organizationId), token)
				.contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isForbidden());
		http.perform(auth(put("/api/v1/kiosk-settings/branches/{id}", foreign.branchId), token)
				.contentType("application/json").content(KIOSK_SETTINGS)).andExpect(status().isBadRequest());
		http.perform(auth(put("/api/v1/kiosk-settings/branches/{id}", tenant.branchId), token)
				.contentType("application/json").content(KIOSK_SETTINGS)).andExpect(status().isOk());
		assertThat(jdbc.sql("""
				SELECT origin FROM audit_log WHERE organization_id=:org AND branch_id=:branch
				  AND actor_user_id=:actor AND action='KIOSK_BRANCH_SETTINGS_UPDATED' AND entity_id=:branch
				""").param("org", tenant.organizationId).param("branch", tenant.branchId).param("actor", platform)
				.query(String.class).single()).isEqualTo("PLATFORM_SUPPORT");
		String expired = jwt.accessToken(new GymOsPrincipal(platform, AccountScope.PLATFORM, tenant.organizationId,
				tenant.branchId, "support", "Soporte QA", Set.of("branch.read"), admin.roles(), true, java.time.Instant.EPOCH));
		http.perform(auth(get("/api/v1/auth/me"), expired)).andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
		return request.header("Authorization", "Bearer " + token);
	}

	private String token(UUID user, UUID branch, Set<String> permissions, String role) {
		return jwt.accessToken(new GymOsPrincipal(user, tenant.organizationId, branch, "fixture", "Fixture", permissions, Set.of(role), false));
	}

	private UUID northClient() {
		UUID client = tenant.client();
		jdbc.sql("UPDATE client SET home_branch_id=:branch WHERE id=:id").param("branch", north).param("id", client).update();
		return client;
	}

	private UUID resource(UUID organization, UUID branch) {
		UUID resource = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO resource(id, organization_id, branch_id, name, resource_type, status, created_at, updated_at)
				VALUES (:id, :org, :branch, 'Cancha', 'ROOM', 'ACTIVE', now(), now())
				""").param("id", resource).param("org", organization).param("branch", branch).update();
		return resource;
	}

	private UUID sale(UUID organization, UUID branch, long amount) {
		UUID sale = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO sale(id, organization_id, branch_id, sale_number, status, subtotal_cents, total_cents,
				                 paid_cents, balance_cents, currency, sold_at, created_at, updated_at)
				VALUES (:id, :org, :branch, :number, 'PAID', :amount, :amount, :amount, 0, 'MXN', now(), now(), now())
				""").param("id", sale).param("org", organization).param("branch", branch)
				.param("number", sale.toString()).param("amount", amount).update();
		return sale;
	}

	private UUID saleForClient(UUID organization, UUID branch, UUID client) {
		UUID id = sale(organization, branch, 100);
		jdbc.sql("UPDATE sale SET client_id=:client WHERE id=:id").param("client", client).param("id", id).update();
		return id;
	}

	private UUID booking(UUID organization, UUID branch, UUID client) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO booking(id, organization_id, branch_id, resource_id, client_id, status,
				                    starts_at, ends_at, created_at, updated_at)
				VALUES (:id,:org,:branch,:resource,:client,'RESERVED',now(),now()+interval '1 hour',now(),now())
				""").param("id", id).param("org", organization).param("branch", branch)
				.param("resource", resource(organization, branch)).param("client", client).update();
		return id;
	}

	private String email(UUID client) {
		return jdbc.sql("SELECT p.email FROM client c JOIN person p ON p.id=c.person_id WHERE c.id=:id")
				.param("id", client).query(String.class).single();
	}

	private UUID payment(UUID organization, UUID branch, UUID sale) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO payment(id, organization_id, branch_id, sale_id, method, status,
				                    amount_cents, currency, received_at, created_at, updated_at)
				VALUES (:id,:org,:branch,:sale,'TRANSFER','PENDING_VALIDATION',100,'MXN',now(),now(),now())
				""").param("id", id).param("org", organization).param("branch", branch).param("sale", sale).update();
		return id;
	}

	private UUID manualGrant(OperationalTestData gym, UUID branch, UUID client) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO access_log(id, organization_id, branch_id, client_id, access_type, decision, source,
				                       occurred_at, created_at, created_by, correlation_id, manual_request_id, manual_business_date)
				VALUES (:id,:org,:branch,:client,'ENTRY','GRANTED','MANUAL',now(),now(),:actor,:id,:id,CURRENT_DATE)
				""").param("id", id).param("org", gym.organizationId).param("branch", branch).param("client", client)
				.param("actor", gym.ownerId).update();
		return id;
	}

	private UUID notification(UUID organization, UUID branch) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO notification_log(id, organization_id, branch_id, channel, recipient, body,
				                             status, created_at, updated_at, available_at)
				VALUES (:id, :org, :branch, 'EMAIL', 'test@example.invalid', 'Prueba',
				        'DEAD_LETTER', now(), now(), now())
				""").param("id", id).param("org", organization).param("branch", branch).update();
		return id;
	}

	private void assignBranch(UUID user, UUID branch) {
		jdbc.sql("INSERT INTO user_branch_access(user_id, branch_id, access_scope) VALUES (:user, :branch, 'BRANCH')")
				.param("user", user).param("branch", branch).update();
	}

	private void assignSystemRole(UUID user, String role, String scope) {
		assertThat(jdbc.sql("""
				INSERT INTO user_role(user_id, role_id)
				SELECT :user, r.id FROM role r WHERE r.name=:role AND r.account_scope=:scope
				  AND r.organization_id IS NULL AND r.system_role AND r.deleted_at IS NULL
				""").param("user", user).param("role", role).param("scope", scope).update()).isEqualTo(1);
	}

	private void grant(UUID user, String permission) {
		jdbc.sql("""
				INSERT INTO role_permission(role_id, permission_id)
				SELECT ur.role_id, p.id FROM user_role ur JOIN permission p ON p.code=:permission
				WHERE ur.user_id=:user
				""").param("user", user).param("permission", permission).update();
	}

	private int auditCount(UUID organization, UUID branch, String action, UUID entity) {
		return jdbc.sql("""
				SELECT count(*) FROM audit_log WHERE organization_id=:org AND branch_id=:branch
				  AND action=:action AND entity_id=:entity
				""").param("org", organization).param("branch", branch).param("action", action)
				.param("entity", entity).query(Integer.class).single();
	}
}
