package com.gymos.bookings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.application.command.CreateResourceCommand;
import com.gymos.bookings.infrastructure.BookingPlanRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository.ResourceRecord;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.ClientBookingConflictRepository;
import com.gymos.catalog.application.port.CatalogPlanCommandPort;
import com.gymos.catalog.application.port.CatalogPlanCommandPort.PlanMutation;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.infrastructure.CatalogPlanCommandRepository;
import com.gymos.catalog.infrastructure.CatalogProductCommandRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.application.DefaultEligibilityPolicy;
import com.gymos.eligibility.infrastructure.EligibilityRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
class BookingMembershipPlanIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private CatalogProductCommandPort catalogProducts;
	private CatalogPlanCommandPort catalogPlans;
	private BookingService bookingService;

	private UUID organizationId;
	private UUID branchId;
	private UUID userId;
	private UUID eligibleClientId;
	private UUID clientWithoutMembershipId;
	private UUID allowedPlanId;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.load()
				.clean();
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load()
				.migrate();
		jdbcClient = JdbcClient.create(dataSource);
		catalogProducts = new CatalogProductCommandRepository(jdbcClient);
		catalogPlans = new CatalogPlanCommandRepository(jdbcClient);
		bookingService = new BookingService(
				new BookingRepository(jdbcClient),
				new BookingResourceRepository(jdbcClient),
				new BookingPlanRepository(jdbcClient),
				new DefaultEligibilityPolicy(new EligibilityRepository(jdbcClient)),
				new ClientBookingConflictRepository(jdbcClient),
				new ResourceScheduleResolver(new ObjectMapper()),
				new AuditService(jdbcClient),
				org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class)
		);
		seedTenantCatalogAndMembers();
	}

	@Test
	void bookingAllowedResourceSucceedsWhenNewClientHasActiveInitialMembership() {
		ResourceRecord sauna = bookingService.createResource(
				principal,
				new CreateResourceCommand("Sauna", "ROOM", 1, 60, LocalTime.of(6, 0), LocalTime.of(22, 0), null, java.util.List.of(allowedPlanId)),
				"booking-membership-positive"
		);

		OffsetDateTime startsAt = BusinessTime.today()
				.plusDays(2)
				.atTime(10, 0)
				.atZone(BusinessTime.DEFAULT_ZONE)
				.toOffsetDateTime();
		BookingRecord booking = bookingService.createBooking(
				principal,
				new CreateBookingCommand(sauna.id(), eligibleClientId, startsAt, startsAt.plusHours(1), "Clase de prueba"),
				"booking-membership-positive"
		);

		assertThat(booking.status()).isEqualTo("RESERVED");
		assertThat(booking.clientId()).isEqualTo(eligibleClientId);
		assertThat(booking.resourceId()).isEqualTo(sauna.id());
	}

	@Test
	void bookingAllowedResourceRejectsClientWithoutActiveMembership() {
		ResourceRecord sauna = bookingService.createResource(
				principal,
				new CreateResourceCommand("Sauna", "ROOM", 1, 60, LocalTime.of(6, 0), LocalTime.of(22, 0), null, java.util.List.of(allowedPlanId)),
				"booking-membership-negative"
		);
		OffsetDateTime startsAt = BusinessTime.today()
				.plusDays(2)
				.atTime(11, 0)
				.atZone(BusinessTime.DEFAULT_ZONE)
				.toOffsetDateTime();

		assertThatThrownBy(() -> bookingService.createBooking(
				principal,
				new CreateBookingCommand(sauna.id(), clientWithoutMembershipId, startsAt, startsAt.plusHours(1), null),
				"booking-membership-negative"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("membresia permitida");
	}

	@Test
	void kioskEligibleCatalogHidesRestrictedResourcesBeforeAvailabilityIsRequested() {
		ResourceRecord restricted = bookingService.createResource(
				principal,
				new CreateResourceCommand("Sauna restringido", "ROOM", 1, 60,
						LocalTime.of(6, 0), LocalTime.of(22, 0), null, java.util.List.of(allowedPlanId)),
				"resource-eligibility"
		);
		ResourceRecord open = bookingService.createResource(
				principal,
				new CreateResourceCommand("Sala abierta", "ROOM", 1, 60,
						LocalTime.of(6, 0), LocalTime.of(22, 0), null, java.util.List.of()),
				"resource-eligibility"
		);
		LocalDate date = BusinessTime.today().plusDays(2);

		assertThat(bookingService.eligibleResources(principal, clientWithoutMembershipId, date))
				.extracting(ResourceRecord::id)
				.contains(open.id())
				.doesNotContain(restricted.id());
		assertThatThrownBy(() -> bookingService.availabilityForClient(
				principal, clientWithoutMembershipId, restricted.id(), date
		))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("BOOKING_PLAN_NOT_ALLOWED"));
	}

	@Test
	void resourceCanBeUpdatedAndSoftDeleted() {
		ResourceRecord resource = bookingService.createResource(
				principal,
				new CreateResourceCommand("Salon uno", "ROOM", 1, 60, LocalTime.of(6, 0), LocalTime.of(22, 0), null, java.util.List.of()),
				"resource-update"
		);

		ResourceRecord updated = bookingService.updateResource(
				principal,
				resource.id(),
				new CreateResourceCommand("Salon renovado", "ROOM", 1, 45, LocalTime.of(7, 0), LocalTime.of(21, 0), "Horario nuevo", java.util.List.of(allowedPlanId)),
				"resource-update"
		);

		assertThat(updated.name()).isEqualTo("Salon renovado");
		assertThat(updated.slotMinutes()).isEqualTo(45);
		assertThat(updated.allowedPlanIds()).containsExactly(allowedPlanId);

		bookingService.deleteResource(principal, resource.id(), "resource-delete");

		assertThat(bookingService.resources(principal))
				.extracting(ResourceRecord::id)
				.doesNotContain(resource.id());
	}

	@Test
	void availabilityStopsAtClosingTimeWithoutWrappingToTheNextDay() {
		ResourceRecord resource = bookingService.createResource(
				principal,
				new CreateResourceCommand(
						"Cancha vespertina", "COURT", 1, 120,
						LocalTime.of(12, 0), LocalTime.of(22, 0), null, java.util.List.of()
				),
				"availability-closing-time"
		);

		var slots = bookingService.availability(principal, resource.id(), BusinessTime.today().plusDays(2));

		assertThat(slots).hasSize(5);
		assertThat(slots)
				.extracting(slot -> slot.startsAt().toLocalTime())
				.containsExactly(
						LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(16, 0),
						LocalTime.of(18, 0), LocalTime.of(20, 0)
				);
		assertThat(slots.getLast().endsAt().toLocalTime()).isEqualTo(LocalTime.of(22, 0));
	}

	private void seedTenantCatalogAndMembers() {
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		eligibleClientId = UUID.randomUUID();
		clientWithoutMembershipId = UUID.randomUUID();
		allowedPlanId = UUID.randomUUID();
		principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"test.reception",
				"Test Reception",
				Set.of("booking.manage", "resource.manage")
		);

		insertOrganizationAndBranch();
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		catalogProducts.create(new ProductMutation(
				organizationId,
				productId,
				"PLAN-BOOKING",
				"Plan Reservas",
				"Plan usado para validar reservas",
				"MEMBERSHIP_PLAN",
				70000,
				"MXN",
				true,
				"ACTIVE",
				null,
				null,
				null,
				null,
				false,
				0,
				null,
				now,
				userId
		));
		catalogPlans.create(new PlanMutation(
				organizationId,
				allowedPlanId,
				productId,
				30,
				3,
				"{}",
				false,
				"ACTIVE",
				now,
				userId
		));
		insertClient(eligibleClientId, "Miranda", "Guajardo", "+523314532321");
		insertClient(clientWithoutMembershipId, "Cliente", "Sin Membresia", "+523314532322");
		insertActiveMembership(eligibleClientId, allowedPlanId);
	}

	private void insertOrganizationAndBranch() {
		jdbcClient.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version
				)
				VALUES (:id, 'Integration Gym Legal', 'Integration Gym', 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""")
				.param("id", organizationId)
				.update();
		jdbcClient.sql("""
				INSERT INTO branch (
				    id, organization_id, name, code, timezone, status, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, 'Centro', 'CENTRO', 'America/Mexico_City', 'ACTIVE', now(), now(), 0)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.update();
	}

	private void insertClient(UUID clientId, String firstName, String lastName, String phone) {
		UUID personId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, maternal_last_name, phone, status,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :firstName, :lastName, '', :phone, 'ACTIVE', now(), now(), 0)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("firstName", firstName)
				.param("lastName", lastName)
				.param("phone", phone)
				.update();
		jdbcClient.sql("""
				INSERT INTO client (
				    id, organization_id, home_branch_id, person_id, status, joined_at,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :personId, 'ACTIVE', now(), now(), now(), 0)
				""")
				.param("id", clientId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("personId", personId)
				.update();
	}

	private void insertActiveMembership(UUID clientId, UUID planId) {
		UUID saleId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status, subtotal_cents,
				    discount_cents, total_cents, paid_cents, balance_cents, currency,
				    sold_at, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PAID', 70000,
				    0, 70000, 70000, 0, 'MXN', now(), now(), now(), 0
				)
				""")
				.param("id", saleId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("saleNumber", "S-" + saleId.toString().substring(0, 8))
				.update();
		LocalDate startsOn = BusinessTime.today().minusDays(1);
		LocalDate endsOn = BusinessTime.today().plusDays(29);
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    now(), :startsOn, :endsOn, :graceEndsOn, now(), now(), 0
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("planId", planId)
				.param("saleId", saleId)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", endsOn)
				.update();
	}
}
