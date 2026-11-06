package com.gymos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.common.security.SessionBinding;
import com.gymos.common.security.SessionContext;
import com.gymos.common.security.SessionRegistry;
import com.gymos.common.security.RefreshCredentials;
import com.gymos.common.security.UserCredentialVersionRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gymos.payments.application.MercadoPagoProviderService;
import com.gymos.payments.api.MercadoPagoPointOperations.PointOrderResult;
import com.gymos.payments.api.events.PointPaymentClosed;
import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Tag("postgres-integration")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
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
	@Autowired ApplicationEventPublisher events;
	@MockitoBean MercadoPagoProviderService pointProvider;
	@Autowired JwtService jwt;
	@Autowired SessionRegistry sessions;
	@Autowired RefreshCredentials refreshCredentials;
	@Autowired UserCredentialVersionRepository credentials;
	@Autowired StringRedisTemplate redisTemplate;
	private static final String SLOT = "00000000-0000-4000-8000-000000000001";
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
		String outside = access(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
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
		grant(tenant.ownerId, "client.read");
		String token = token(tenant.ownerId, tenant.branchId, Set.of("branch.read", "client.read"), "Gerente");
		String separateSession = token(tenant.ownerId, tenant.branchId, Set.of("branch.read", "client.read"), "Gerente");
		for (UUID unavailable : Set.of(north, foreign.branchId)) {
			http.perform(transition(post("/api/v1/auth/select-branch"), token).contentType("application/json")
							.content("{\"branchId\":\"%s\"}".formatted(unavailable)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BRANCH_ACCESS_DENIED"));
		}
		assignBranch(tenant.ownerId, north);
		var selected = http.perform(transition(post("/api/v1/auth/select-branch"), token).contentType("application/json")
						.content("{\"branchId\":\"%s\"}".formatted(north)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.branchId").value(north.toString())).andReturn();
		String northToken = json.readTree(selected.getResponse().getContentAsByteArray()).path("accessToken").asText();
		http.perform(auth(get("/api/v1/clients/{id}", client), northToken)).andExpect(status().isOk());
		var northSession = browserSession(selected, SLOT);
		var renewed = browserSession(refresh(northSession.cookie(), SLOT, UUID.randomUUID()), SLOT);
		http.perform(auth(get("/api/v1/clients/{id}", client), renewed.access())).andExpect(status().isOk());
		assertThat(auditCount(tenant.organizationId, north, "BRANCH_SELECTED", north)).isEqualTo(1);
		jdbc.sql("DELETE FROM user_branch_access WHERE user_id=:user AND branch_id=:branch")
				.param("user", tenant.ownerId).param("branch", north).update();
		http.perform(auth(get("/api/v1/clients/{id}", client), northToken)).andExpect(status().isUnauthorized());
		assertThat(refresh(renewed.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
		http.perform(auth(get("/api/v1/auth/me"), separateSession)).andExpect(status().isOk());
		jdbc.sql("UPDATE organization SET status='SUSPENDED' WHERE id=:id").param("id", tenant.organizationId).update();
		http.perform(auth(get("/api/v1/auth/me"), separateSession)).andExpect(status().isUnauthorized());
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
		String outside = access(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
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
		var otherSession = json.readTree(http.perform(auth(post(launchPath), access(foreignReception))).andExpect(status().isOk())
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
		var selected = http.perform(transition(post("/api/v1/auth/select-branch"), staffToken)
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
							.content("{\"method\":\"CASH\",\"amountCents\":10000,\"validateNow\":false}"))
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
			var loggedIn = http.perform(browser(post("/api/v1/auth/login")).contentType("application/json")
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
		String adminToken = access(admin);
		http.perform(auth(get("/api/v1/platform/customers"), adminToken)).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/clients/{id}", tenant.client()), adminToken)).andExpect(status().isForbidden());
		var selected = http.perform(transition(post("/api/v1/auth/select-organization"), adminToken)
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
		var support = browserSession(selected, SLOT);
		var supportRenewal = refresh(support.cookie(), SLOT, UUID.randomUUID());
		var renewed = browserSession(supportRenewal, SLOT);
		String expiresAt = json.readTree(selected.getResponse().getContentAsByteArray()).path("user").path("supportExpiresAt").asText();
		assertThat(json.readTree(supportRenewal.getResponse().getContentAsByteArray())
				.path("user").path("supportExpiresAt").asText()).isEqualTo(expiresAt);
		http.perform(auth(get("/api/v1/platform/customers"), adminToken)).andExpect(status().isUnauthorized());
		var exit = http.perform(browser(auth(post("/api/v1/auth/exit-support"), renewed.access())).cookie(renewed.cookie()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.supportMode").value(false)).andReturn();
		http.perform(auth(get("/api/v1/platform/customers"), browserSession(exit, SLOT).access())).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/auth/me"), renewed.access())).andExpect(status().isUnauthorized());
		String expired = access(new GymOsPrincipal(platform, AccountScope.PLATFORM, tenant.organizationId,
				tenant.branchId, "support", "Soporte QA", Set.of("branch.read"), admin.roles(), true, java.time.Instant.EPOCH));
		http.perform(auth(get("/api/v1/auth/me"), expired)).andExpect(status().isUnauthorized());
		String fingerprint = credentials.authorityFingerprint(platform);
		Instant expires = Instant.now().plusSeconds(3600);
		var expiredContext = new SessionContext(platform, AccountScope.PLATFORM, tenant.organizationId,
				tenant.branchId, true, Instant.EPOCH, 0, fingerprint, expires);
		UUID expiredId = sessions.create(expiredContext.serialize(), expires);
		assertThat(refresh(new Cookie("__Host-gymfort-refresh-" + SLOT, refreshCredentials.issue(expiredId, 0)),
				SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void refreshRotationIsAtomicAndRetiredCredentialsRevokeOnlyTheirFamily() throws Exception {
		UUID user = loginUser();
		var initial = loginSession(user, SLOT);
		var other = loginSession(user, UUID.randomUUID().toString());
		UUID operation = UUID.randomUUID();
		var start = new CyclicBarrier(2);
		MvcResult first;
		MvcResult second;
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			java.util.concurrent.Callable<MvcResult> request = () -> {
				start.await(10, TimeUnit.SECONDS);
				return refresh(initial.cookie(), SLOT, operation);
			};
			var a = executor.submit(request);
			var b = executor.submit(request);
			first = a.get(15, TimeUnit.SECONDS);
			second = b.get(15, TimeUnit.SECONDS);
		}
		var renewed = browserSession(first, SLOT);
		var retried = browserSession(second, SLOT);
		assertThat(renewed.cookie().getValue().equals(retried.cookie().getValue())).isTrue();
		assertThat(renewed.cookie().getValue().equals(initial.cookie().getValue())).isFalse();
		assertThat(refresh(initial.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(409);
		http.perform(auth(get("/api/v1/auth/me"), renewed.access())).andExpect(status().isOk());
		var binding = jwt.parseAccessToken(initial.access()).orElseThrow().session();
		String key = "gymos:session:v3:" + binding.id();
		assertThat(redisTemplate.opsForHash().values(key).contains(renewed.cookie().getValue())).isFalse();
		redisTemplate.opsForHash().put(key, "retryUntil", "0");
		assertThat(refresh(initial.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(auth(get("/api/v1/auth/me"), renewed.access())).andExpect(status().isUnauthorized());
		assertThat(refresh(renewed.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(auth(get("/api/v1/auth/me"), other.access())).andExpect(status().isOk());
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "AUTH_REFRESH_REUSED", user)).isEqualTo(1);
	}

	@Test
	void browserCookieIsProtectedAndLogoutRevokesAccessWithoutAValidBearer(CapturedOutput output) throws Exception {
		UUID user = loginUser();
		var session = loginSession(user, SLOT);
		String otherSlot = UUID.randomUUID().toString();
		var other = loginSession(user, otherSlot);
		http.perform(post("/api/v1/auth/refresh").cookie(session.cookie()).header("X-GymFort-Session", SLOT)
						.header("X-GymFort-Refresh-Request", UUID.randomUUID()))
				.andExpect(status().isForbidden());
		http.perform(post("/api/v1/auth/logout").cookie(session.cookie()).header("Origin", "https://untrusted.invalid")
						.header("X-GymFort-Session", SLOT))
				.andExpect(status().isForbidden());
		http.perform(post("/api/v1/auth/logout").cookie(session.cookie()).header("Origin", "http://localhost:3000"))
				.andExpect(status().isForbidden());
		assertThat(refresh(session.cookie(), otherSlot, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(auth(get("/api/v1/auth/me"), session.access())).andExpect(status().isOk());
		http.perform(browser(post("/api/v1/auth/logout")).cookie(session.cookie()))
				.andExpect(status().isNoContent()).andExpect(cookie().maxAge(session.cookie().getName(), 0));
		http.perform(auth(get("/api/v1/auth/me"), session.access())).andExpect(status().isUnauthorized());
		assertThat(refresh(session.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(auth(get("/api/v1/auth/me"), other.access())).andExpect(status().isOk());
		assertThat(auditCount(tenant.organizationId, tenant.branchId, "AUTH_LOGOUT", user)).isEqualTo(1);
		assertThat(output.getAll().contains(session.cookie().getValue())
				|| output.getAll().contains(session.access()) || output.getAll().contains("SessionFixtureQA!2026")).isFalse();
	}

	@Test
	void recoveryAndDeactivationRevokeBothAccessAndRefresh() throws Exception {
		UUID staff = loginUser();
		var session = loginSession(staff, SLOT);
		var other = loginSession(staff, UUID.randomUUID().toString());
		String owner = token(tenant.ownerId, tenant.branchId, Set.of("user.manage"), "Dueno del gimnasio");
		var issued = http.perform(auth(post("/api/v1/users/internal/{id}/password-recovery", staff), owner))
				.andExpect(status().isOk()).andReturn();
		String reset = json.readTree(issued.getResponse().getContentAsByteArray()).path("resetUrl").asText().split("#token=")[1];
		http.perform(post("/api/v1/auth/password-recovery/accept").contentType("application/json")
				.content(json.writeValueAsString(java.util.Map.of("token", reset, "password", "NextSessionQA!2026"))))
				.andExpect(status().isOk());
		http.perform(auth(get("/api/v1/auth/me"), session.access())).andExpect(status().isUnauthorized());
		http.perform(auth(get("/api/v1/auth/me"), other.access())).andExpect(status().isUnauthorized());
		assertThat(refresh(session.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		http.perform(loginRequest(staff, SLOT, "BadSessionQA!2026")).andExpect(status().isUnauthorized());
		var renewed = browserSession(http.perform(loginRequest(staff, SLOT, "NextSessionQA!2026")).andReturn(), SLOT);
		http.perform(auth(patch("/api/v1/users/internal/{id}/status", staff), owner)
						.contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
				.andExpect(status().isOk());
		http.perform(auth(get("/api/v1/auth/me"), renewed.access())).andExpect(status().isUnauthorized());
		assertThat(refresh(renewed.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void withdrawingAnEffectivePermissionInvalidatesEverySessionImmediately() throws Exception {
		UUID user = loginUser();
		grant(user, "client.read");
		var session = loginSession(user, SLOT);
		http.perform(auth(get("/api/v1/clients/search"), session.access())).andExpect(status().isOk());
		jdbc.sql("""
				DELETE FROM role_permission rp USING user_role ur, permission p
				WHERE ur.user_id=:id AND ur.role_id=rp.role_id AND p.id=rp.permission_id AND p.code='client.read'
				""").param("id", user).update();
		http.perform(auth(get("/api/v1/clients/search"), session.access())).andExpect(status().isUnauthorized());
		assertThat(refresh(session.cookie(), SLOT, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
		var renewed = loginSession(user, SLOT);
		http.perform(auth(get("/api/v1/auth/me"), renewed.access())).andExpect(status().isOk());
		http.perform(auth(get("/api/v1/clients/search"), renewed.access())).andExpect(status().isForbidden());
	}

	private UUID loginUser() {
		return tenant.user("Recepcion", passwordEncoder.encode("SessionFixtureQA!2026"));
	}

	private BrowserSession loginSession(UUID user, String slot) throws Exception {
		return browserSession(http.perform(loginRequest(user, slot, "SessionFixtureQA!2026")).andReturn(), slot);
	}

	private MockHttpServletRequestBuilder loginRequest(UUID user, String slot, String password) throws Exception {
		return post("/api/v1/auth/login").header("Origin", "http://localhost:3000").header("X-GymFort-Session", slot)
				.contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username", user, "password", password)));
	}

	private BrowserSession browserSession(MvcResult result, String slot) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		String name = "__Host-gymfort-refresh-" + slot;
		String header = result.getResponse().getHeader("Set-Cookie");
		assertThat(header != null && header.startsWith(name + "=") && header.contains("HttpOnly")
				&& header.contains("Secure") && header.contains("SameSite=Strict") && header.contains("Path=/")
				&& !header.contains("Domain=")).isTrue();
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		var response = json.readTree(result.getResponse().getContentAsByteArray());
		assertThat(response.has("refreshToken")).isFalse();
		String value = header.substring(name.length() + 1, header.indexOf(';'));
		return new BrowserSession(response.path("accessToken").asText(), new Cookie(name, value));
	}

	private MvcResult refresh(Cookie cookie, String slot, UUID operation) throws Exception {
		return http.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000")
				.header("X-GymFort-Session", slot).header("X-GymFort-Refresh-Request", operation).cookie(cookie)).andReturn();
	}

	private record BrowserSession(String access, Cookie cookie) {
		@Override public String toString() { return "BrowserSession[redacted]"; }
	}

	private MockHttpServletRequestBuilder browser(MockHttpServletRequestBuilder request) {
		return request.header("Origin", "http://localhost:3000").header("X-GymFort-Session", SLOT);
	}

	private MockHttpServletRequestBuilder transition(MockHttpServletRequestBuilder request, String accessToken) {
		var binding = jwt.parseAccessToken(accessToken).orElseThrow().session();
		return browser(auth(request, accessToken))
				.cookie(new Cookie("__Host-gymfort-refresh-" + SLOT, refreshCredentials.issue(binding.id(), 0)));
	}

	private String access(GymOsPrincipal principal) {
		String fingerprint = credentials.authorityFingerprint(principal.userId());
		Instant expiresAt = Instant.now().plusSeconds(3600);
		UUID id = sessions.create(SessionContext.from(principal, fingerprint, expiresAt).serialize(), expiresAt);
		return jwt.accessToken(principal.withSession(new SessionBinding(id, 0, fingerprint)));
	}

	private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
		return request.header("Authorization", "Bearer " + token);
	}

	@Test
	void simultaneousCashSalesForLastUnitCommitExactlyOneSalePaymentAndMovement() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String cashier = token(tenant.ownerId, tenant.branchId, Set.of("sale.create"), "Recepcion");
		var results = race(java.util.List.of(
				() -> retail(cashier, product, "last-a", 100),
				() -> retail(cashier, product, "last-b", 100)
		));
		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 400);
		assertThat(json.readTree(results.stream().filter(result -> result.getResponse().getStatus() == 400)
				.findFirst().orElseThrow().getResponse().getContentAsString()).path("code").asText())
				.isEqualTo("INVENTORY_STOCK_INSUFFICIENT");
		assertThat(count("SELECT count(*) FROM sale WHERE organization_id=:id AND status='PAID'", tenant.organizationId)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM payment WHERE organization_id=:id AND status='COMPLETED'", tenant.organizationId)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE product_id=:id AND movement_type='SALE'", product)).isEqualTo(1);
		assertThat(count("SELECT quantity_on_hand FROM inventory_stock WHERE product_id=:id", product)).isZero();
	}

	@Test
	void tenIdenticalRequestsAndTimeoutReplayReturnOriginalPersistedSale() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String cashier = token(tenant.ownerId, tenant.branchId, Set.of("sale.create"), "Recepcion");
		java.util.List<java.util.concurrent.Callable<MvcResult>> attempts = java.util.stream.IntStream.range(0, 10)
				.mapToObj(index -> (java.util.concurrent.Callable<MvcResult>) () -> retail(cashier, product, "same-request", 100))
				.toList();
		var responses = race(attempts);
		UUID saleId = saleId(responses.getFirst());
		for (MvcResult response : responses) assertThat(saleId(response)).isEqualTo(saleId);
		// Discarding the first response models the server committing before the client receives it.
		assertThat(saleId(retail(cashier, product, "same-request", 100))).isEqualTo(saleId);
		assertThat(retail(cashier, product, "same-request", 99).getResponse().getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
		assertThat(count("SELECT count(*) FROM sale WHERE organization_id=:id", tenant.organizationId)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM payment WHERE sale_id=:id", saleId)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE reference_id=:id", saleId)).isEqualTo(1);
	}

	@Test
	void concurrentPaymentsAndRefundsUsePersistedRemainingBalance() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String manager = token(tenant.ownerId, tenant.branchId,
				Set.of("sale.create", "payment.manual.create", "sale.refund"), "Gerente");
		UUID saleId = saleId(retail(manager, product, "unpaid", 0));
		var samePayments = race(java.util.List.of(
				() -> pay(manager, saleId, "payment-same", 60),
				() -> pay(manager, saleId, "payment-same", 60)));
		for (MvcResult response : samePayments) assertThat(saleId(response)).isEqualTo(saleId);
		assertThat(count("SELECT count(*) FROM payment WHERE sale_id=:id", saleId)).isEqualTo(1);
		assertThat(count("SELECT balance_cents FROM sale WHERE id=:id", saleId)).isEqualTo(40);
		var competingPayments = race(java.util.List.of(
				() -> pay(manager, saleId, "payment-a", 40),
				() -> pay(manager, saleId, "payment-b", 40)));
		assertThat(competingPayments).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 400);
		assertThat(count("SELECT paid_cents FROM sale WHERE id=:id", saleId)).isEqualTo(100);
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE reference_id=:id", saleId)).isEqualTo(1);
		var refunds = race(java.util.List.of(
				() -> saleMutation(manager, saleId, "refunds", "{\"amountCents\":60,\"reason\":\"Devolución\",\"idempotencyKey\":\"refund-a\"}"),
				() -> saleMutation(manager, saleId, "refunds", "{\"amountCents\":60,\"reason\":\"Devolución\",\"idempotencyKey\":\"refund-b\"}")));
		assertThat(refunds).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 400);
		assertThat(count("SELECT SUM(amount_cents) FROM sale_refund WHERE sale_id=:id AND status='PROCESSED'", saleId)).isEqualTo(60);
		assertThat(count("SELECT quantity_on_hand FROM inventory_stock WHERE product_id=:id", product)).isZero();
	}

	@Test
	void cancellationAndPaymentCompeteAndForeignActorsCannotCancel() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String manager = token(tenant.ownerId, tenant.branchId,
				Set.of("sale.create", "payment.manual.create", "sale.refund"), "Gerente");
		UUID saleId = saleId(retail(manager, product, "cancel-race", 0));
		String reason = "{\"reason\":\"Captura equivocada\"}";
		String cashier = token(tenant.ownerId, tenant.branchId, Set.of("sale.create"), "Recepcion");
		assertThat(saleMutation(cashier, saleId, "cancel", reason).getResponse().getStatus()).isEqualTo(403);
		String outside = access(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
				"fixture", "Fixture", Set.of("sale.refund"), Set.of("Gerente"), false));
		assertThat(saleMutation(outside, saleId, "cancel", reason).getResponse().getContentAsString()).contains("SALE_NOT_FOUND");
		var results = race(java.util.List.of(
				() -> pay(manager, saleId, "race-payment", 100),
				() -> saleMutation(manager, saleId, "cancel", reason)));
		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 400);
		boolean cancelled = results.get(1).getResponse().getStatus() == 200;
		assertThat(count("SELECT paid_cents FROM sale WHERE id=:id", saleId)).isEqualTo(cancelled ? 0 : 100);
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE reference_id=:id", saleId)).isEqualTo(cancelled ? 0 : 1);
		if (cancelled) {
			assertThat(saleId(saleMutation(manager, saleId, "cancel", reason))).isEqualTo(saleId);
			assertThat(auditCount(tenant.organizationId, tenant.branchId, "SALE_CANCELLED", saleId)).isEqualTo(1);
		}
	}

	@Test
	void simultaneousBranchesAndTenantsKeepIndependentStockAndKeys() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId, north);
		UUID otherProduct = stockProduct(foreign, foreign.branchId);
		jdbc.sql("INSERT INTO user_branch_access(user_id,branch_id,access_scope) VALUES (:user,:branch,'BRANCH')")
				.param("user", tenant.ownerId).param("branch", north).update();
		String southCashier = token(tenant.ownerId, tenant.branchId, Set.of("sale.create"), "Recepcion");
		String northCashier = token(tenant.ownerId, north, Set.of("sale.create"), "Recepcion");
		String foreignCashier = access(new GymOsPrincipal(foreign.ownerId, foreign.organizationId, foreign.branchId,
				"fixture", "Fixture", Set.of("sale.create"), Set.of("Recepcion"), false));
		var results = race(java.util.List.of(
				() -> retail(southCashier, product, "shared-key", 100),
				() -> retail(northCashier, product, "north-key", 100),
				() -> retail(foreignCashier, otherProduct, "shared-key", 100)));
		for (MvcResult result : results) saleId(result);
		assertThat(retail(northCashier, product, "shared-key", 100).getResponse().getContentAsString())
				.contains("IDEMPOTENCY_KEY_REUSED").doesNotContain(saleId(results.getFirst()).toString());
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE product_id=:id", product)).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM inventory_stock WHERE product_id=:id AND quantity_on_hand=0", product)).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE product_id=:id", otherProduct)).isEqualTo(1);
	}

	@Test
	void pointReservesBeforeSendingAndConsumesTheReservationOnlyOnceOnConfirmation() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String manager = pointManager();
		UUID saleId = saleId(retail(manager, product, "point-sale", 0));
		UUID paymentId = preparePoint(manager, saleId);
		assertThat(count("SELECT count(*) FROM inventory_sale_reservation WHERE sale_id=:id", saleId)).isEqualTo(1);
		assertThat(count("SELECT quantity_on_hand FROM inventory_stock WHERE product_id=:id", product)).isEqualTo(1);
		assertThat(retail(manager, product, "competing-cash", 100).getResponse().getContentAsString())
				.contains("INVENTORY_STOCK_RESERVED");
		var completion = new ProviderPaymentCompletedV1("point-confirmed", tenant.organizationId, tenant.branchId,
				"MERCADO_PAGO", UUID.randomUUID(), "ORD-fixture", saleId, paymentId, 100, "MXN", OffsetDateTime.now());
		events.publishEvent(completion);
		events.publishEvent(completion);
		assertThat(count("SELECT count(*) FROM inventory_sale_reservation WHERE sale_id=:id", saleId)).isZero();
		assertThat(count("SELECT quantity_on_hand FROM inventory_stock WHERE product_id=:id", product)).isZero();
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE reference_id=:id", saleId)).isEqualTo(1);
		assertThat(count("SELECT paid_cents FROM sale WHERE id=:id", saleId)).isEqualTo(100);
	}

	@Test
	void pointTerminalClosureReleasesTheReservationAndDoesNotCreateAStockMovement() throws Exception {
		UUID product = stockProduct(tenant, tenant.branchId);
		String manager = pointManager();
		UUID saleId = saleId(retail(manager, product, "point-expired", 0));
		UUID paymentId = preparePoint(manager, saleId);
		var expired = new PointPaymentClosed(tenant.organizationId, tenant.branchId, saleId, paymentId, "EXPIRED");
		events.publishEvent(expired);
		events.publishEvent(expired);
		assertThat(count("SELECT count(*) FROM inventory_sale_reservation WHERE sale_id=:id", saleId)).isZero();
		assertThat(count("SELECT count(*) FROM inventory_movement WHERE reference_id=:id", saleId)).isZero();
		assertThat(count("SELECT count(*) FROM payment WHERE id=:id AND status='REJECTED'", paymentId)).isEqualTo(1);
		assertThat(count("SELECT paid_cents FROM sale WHERE id=:id", saleId)).isZero();
		saleId(retail(manager, product, "cash-after-expiry", 100));
	}

	private String pointManager() {
		jdbc.sql("""
				INSERT INTO organization_payment_configuration(organization_id, mercado_pago_account_id, mercado_pago_connected_at, created_at, updated_at)
				VALUES (:org, 'point-fixture', now(), now(), now())
				""").param("org", tenant.organizationId).update();
		jdbc.sql("UPDATE branch SET reception_mercado_pago_enabled=true, mercado_pago_terminal_id='terminal-test' WHERE id=:branch")
				.param("branch", tenant.branchId).update();
		org.mockito.Mockito.when(pointProvider.createOrder(org.mockito.ArgumentMatchers.any()))
				.thenReturn(new PointOrderResult(UUID.randomUUID(), "ORD-fixture", "CREATED"));
		return token(tenant.ownerId, tenant.branchId, Set.of("sale.create", "payment.manual.create"), "Gerente");
	}

	private UUID preparePoint(String manager, UUID saleId) throws Exception {
		var response = saleMutation(manager, saleId, "payments", """
				{"method":"CARD_MERCADO_PAGO","amountCents":100,"validateNow":false,"idempotencyKey":"point-payment"}
				""");
		saleId(response);
		UUID paymentId = UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("operationPaymentId").asText());
		http.perform(auth(post("/api/v1/sales/payments/{id}/mercado-pago-order", paymentId), manager)).andExpect(status().isOk());
		return paymentId;
	}

	private UUID stockProduct(OperationalTestData gym, UUID... branches) {
		UUID product = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO catalog_product(id, organization_id, sku, name, product_type, price_cents, currency,
				                            status, inventory_tracked, created_at, updated_at)
				VALUES (:id, :org, :sku, 'Producto concurrente', 'PHYSICAL', 100, 'MXN', 'ACTIVE', true, now(), now())
				""").param("id", product).param("sku", product.toString()).param("org", gym.organizationId).update();
		for (UUID branch : branches) {
			jdbc.sql("INSERT INTO catalog_product_branch(organization_id, branch_id, product_id, created_at) VALUES (:org,:branch,:product,now())")
					.param("org", gym.organizationId).param("branch", branch).param("product", product).update();
			jdbc.sql("""
					INSERT INTO inventory_stock(id, organization_id, branch_id, product_id, quantity_on_hand, created_at, updated_at)
					VALUES (:id,:org,:branch,:product,1,now(),now())
					""").param("id", UUID.randomUUID()).param("org", gym.organizationId).param("branch", branch).param("product", product).update();
		}
		return product;
	}

	private MvcResult retail(String token, UUID product, String key, int amount) throws Exception {
		String payment = amount == 0 ? "null" : """
				{"method":"CASH","amountCents":%d,"validateNow":true}
				""".formatted(amount);
		return http.perform(auth(post("/api/v1/sales"), token).contentType("application/json").content("""
				{"items":[{"productId":"%s","quantity":1,"discountCents":0}],"discountCents":0,"payment":%s,"idempotencyKey":"%s"}
				""".formatted(product, payment, key))).andReturn();
	}

	private MvcResult pay(String token, UUID saleId, String key, int amount) throws Exception {
		return saleMutation(token, saleId, "payments", """
				{"method":"CASH","amountCents":%d,"validateNow":true,"idempotencyKey":"%s"}
				""".formatted(amount, key));
	}

	private MvcResult saleMutation(String token, UUID saleId, String operation, String body) throws Exception {
		return http.perform(auth(post("/api/v1/sales/{saleId}/" + operation, saleId), token)
				.contentType("application/json").content(body)).andReturn();
	}

	private UUID saleId(MvcResult response) throws Exception {
		assertThat(response.getResponse().getStatus()).withFailMessage(response.getResponse().getContentAsString()).isEqualTo(200);
		return UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("sale").path("id").asText());
	}

	private long count(String query, UUID id) {
		return jdbc.sql(query).param("id", id).query(Long.class).single();
	}

	private java.util.List<MvcResult> race(java.util.List<java.util.concurrent.Callable<MvcResult>> actions) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(actions.size());
		try (var executor = Executors.newFixedThreadPool(actions.size())) {
			var pending = actions.stream().map(action -> executor.submit(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return action.call();
			})).toList();
			var results = new java.util.ArrayList<MvcResult>();
			for (var result : pending) results.add(result.get(20, TimeUnit.SECONDS));
			return results;
		}
	}


	private String token(UUID user, UUID branch, Set<String> permissions, String role) {
		return access(new GymOsPrincipal(user, tenant.organizationId, branch, "fixture", "Fixture", permissions, Set.of(role), false));
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
