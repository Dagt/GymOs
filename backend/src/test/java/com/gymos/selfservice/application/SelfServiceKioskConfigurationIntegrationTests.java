package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.bookings.api.operations.BookingOperations;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import com.gymos.memberships.api.operations.MembershipEnrollmentOperations;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.StripeCheckoutOperations;
import com.gymos.payments.api.StripeRecurringOperations;
import com.gymos.selfservice.application.KioskSettingsService.UpdateKioskSettings;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class SelfServiceKioskConfigurationIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
	private static JdbcClient jdbc;

	private UUID organizationId;
	private UUID branchId;
	private UUID userId;
	private KioskSettingsService settingsService;
	private SelfServiceService selfService;
	private AtomicReference<PaymentConfigurationView> payments;

	@BeforeAll
	static void migrate() {
		var source = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(source);
	}

	@BeforeEach
	void setUp() {
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		insertTenantData();
		var repository = new SelfServiceRepository(jdbc);
		var capabilityPolicy = new SelfServiceCapabilityPolicy(new ObjectMapper());
		var paymentConfiguration = mock(PaymentConfigurationOperations.class);
		payments = new AtomicReference<>(pendingStripe());
		when(paymentConfiguration.configuration(organizationId, branchId)).thenAnswer(invocation -> payments.get());
		var audit = mock(AuditService.class);
		settingsService = new KioskSettingsService(
				repository, capabilityPolicy, paymentConfiguration, audit);
		selfService = new SelfServiceService(
				repository,
				mock(MembershipOperations.class),
				mock(MembershipEnrollmentOperations.class),
				mock(ClientLookupOperations.class),
				mock(BookingOperations.class),
				audit,
				mock(RateLimiter.class),
				capabilityPolicy,
				paymentConfiguration,
				mock(StripeCheckoutOperations.class),
				mock(StripeRecurringOperations.class)
		);
	}

	@Test
	void persistedRegistrationAndPaymentIntentReachNewAndResetSessions() {
		GymOsPrincipal manager = principal(Set.of("selfservice.settings.manage"), Set.of("Gerente"));
		GymOsPrincipal reception = principal(Set.of("selfservice.session.launch"), Set.of("Recepcion"));
		UpdateKioskSettings enabled = new UpdateKioskSettings(
				true, true, true, true, true, true, false, true, true);

		var configured = settingsService.configure(manager, branchId, enabled, "settings-enabled");

		assertThat(configured.capabilities().clientRegistrationAllowed()).isTrue();
		assertThat(configured.capabilities().membershipEnrollmentAllowed()).isTrue();
		assertThat(configured.capabilities().visitPackagePurchaseAllowed()).isTrue();
		assertThat(configured.capabilities().onlineCardPaymentsReady()).isFalse();
		assertPersisted("clientRegistration", true);
		assertPersisted("membershipEnrollment", true);
		assertPersisted("visitPackagePurchase", true);
		var launched = selfService.launchSession(reception, "127.0.0.1", "launch-enabled");
		assertThat(launched.capabilities().clientRegistrationAllowed()).isTrue();
		assertThat(launched.capabilities().membershipEnrollmentAllowed()).isTrue();
		assertThat(launched.capabilities().visitPackagePurchaseAllowed()).isTrue();
		assertThat(launched.capabilities().onlineCardPaymentsReady()).isFalse();

		settingsService.configure(
				manager, branchId,
				new UpdateKioskSettings(false, true, true, true, true, true, false, true, true),
				"settings-registration-disabled"
		);
		var resetDisabled = selfService.reset(
				launched.sessionId(), launched.sessionToken(), "reset-disabled");
		assertPersisted("clientRegistration", false);
		assertThat(resetDisabled.capabilities().clientRegistrationAllowed()).isFalse();
		assertThat(resetDisabled.capabilities().membershipEnrollmentAllowed()).isTrue();

		settingsService.configure(manager, branchId, enabled, "settings-registration-enabled-again");
		var resetEnabled = selfService.reset(
				launched.sessionId(), launched.sessionToken(), "reset-enabled");
		assertPersisted("clientRegistration", true);
		assertThat(resetEnabled.capabilities().clientRegistrationAllowed()).isTrue();

		payments.set(readyStripe());
		var resetReady = selfService.reset(
				launched.sessionId(), launched.sessionToken(), "reset-stripe-ready");
		assertThat(resetReady.capabilities().membershipEnrollmentAllowed()).isTrue();
		assertThat(resetReady.capabilities().visitPackagePurchaseAllowed()).isTrue();
		assertThat(resetReady.capabilities().onlineCardPaymentsReady()).isTrue();
	}

	private GymOsPrincipal principal(Set<String> permissions, Set<String> roles) {
		return new GymOsPrincipal(
				userId, organizationId, branchId, "operator@example.test", "Operador",
				permissions, roles, false);
	}

	private void assertPersisted(String key, boolean expected) {
		Boolean value = jdbc.sql("""
				SELECT (allowed_actions_json ->> :key)::boolean
				FROM self_service_terminal
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND device_code = 'KIOSK-MAIN'
				  AND deleted_at IS NULL
				""")
				.param("key", key)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(Boolean.class)
				.single();
		assertThat(value).isEqualTo(expected);
	}

	private void insertTenantData() {
		jdbc.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency,
				    created_at, updated_at, version
				) VALUES (
				    :id, 'Kiosk Test Legal', 'Kiosk Test', 'ACTIVE',
				    'America/Mexico_City', 'MXN', now(), now(), 0
				)
				""").param("id", organizationId).update();
		jdbc.sql("""
				INSERT INTO branch (
				    id, organization_id, name, code, status, timezone,
				    created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, 'Centro', :code, 'ACTIVE',
				    'America/Mexico_City', now(), now(), 0
				)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.param("code", "KIOSK-" + branchId.toString().substring(0, 8))
				.update();
		UUID personId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, email, status,
				    created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, 'Operador', 'Kiosco', :email, 'ACTIVE',
				    now(), now(), 0
				)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("email", userId + "@example.test")
				.update();
		jdbc.sql("""
				INSERT INTO user_account (
				    id, organization_id, person_id, username, password_hash, status,
				    failed_attempts, created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :personId, :username, 'encoded', 'ACTIVE',
				    0, now(), now(), 0
				)
				""")
				.param("id", userId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("username", "operator-" + userId)
				.update();
	}

	private PaymentConfigurationView pendingStripe() {
		return new PaymentConfigurationView(false, false, null, null, false, true, true, null, false);
	}

	private PaymentConfigurationView readyStripe() {
		return new PaymentConfigurationView(false, false, null, null, false, true, true, "acct_test", true);
	}
}
