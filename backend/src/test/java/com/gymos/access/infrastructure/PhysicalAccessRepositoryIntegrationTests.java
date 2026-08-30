package com.gymos.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectorContext;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class PhysicalAccessRepositoryIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	@Test
	void claimsAreExclusiveAndExpiredLeasesCanBeRecoveredAndCompleted() throws Exception {
		Fixture fixture = fixture();
		OffsetDateTime now = OffsetDateTime.now();
		var state = fixture.repository().upsertDesiredState(
				fixture.connection(), fixture.clientId(), "[\"LEVEL-PRIMARY\"]", "UPSERT", "PENDING",
				null, null, "membership:activated:1", null, now);
		var firstJob = fixture.repository().enqueueJob(
				fixture.connection(), state, "person-42", "job-membership-1", "MEMBERSHIP_EVENT", now);
		var duplicateJob = fixture.repository().enqueueJob(
				fixture.connection(), state, "person-42", "job-membership-1", "MEMBERSHIP_EVENT", now);
		assertThat(duplicateJob.id()).isEqualTo(firstJob.id());

		var barrier = new CyclicBarrier(2);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var claimA = executor.submit(() -> {
				barrier.await();
				return repository().claimJobs(
						fixture.connector(), 1, now.plusSeconds(1), now.plusSeconds(31));
			});
			var claimB = executor.submit(() -> {
				barrier.await();
				return repository().claimJobs(
						fixture.connector(), 1, now.plusSeconds(1), now.plusSeconds(31));
			});
			List<PhysicalAccessRepository.SyncJobRecord> claimed = java.util.stream.Stream
					.concat(claimA.get().stream(), claimB.get().stream())
					.toList();
			assertThat(claimed).singleElement().extracting(PhysicalAccessRepository.SyncJobRecord::id)
					.isEqualTo(firstJob.id());
			var originalClaim = claimed.getFirst();
			assertThat(originalClaim.claimToken()).isNotNull();

			var reclaimed = fixture.repository().claimJobs(
					fixture.connector(), 1, now.plusSeconds(32), now.plusSeconds(62));
			assertThat(reclaimed).singleElement().satisfies(job -> {
				assertThat(job.id()).isEqualTo(firstJob.id());
				assertThat(job.attempts()).isEqualTo(2);
				assertThat(job.claimToken()).isNotEqualTo(originalClaim.claimToken());
			});
			assertThat(fixture.repository().completeJob(
					fixture.connector(), firstJob.id(), originalClaim.claimToken(),
					"late-operation", now.plusSeconds(33))).isEmpty();
			var completed = fixture.repository().completeJob(
					fixture.connector(), firstJob.id(), reclaimed.getFirst().claimToken(),
					"provider-operation-1", now.plusSeconds(33));
			assertThat(completed).get().extracting(PhysicalAccessRepository.SyncJobRecord::status)
					.isEqualTo("SUCCEEDED");
			assertThat(fixture.repository().desiredState(
					fixture.organizationId(), fixture.branchId(), fixture.connection().id(), fixture.clientId()))
					.get().extracting(PhysicalAccessRepository.DesiredStateRecord::status).isEqualTo("SYNCED");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void retryableFailuresReturnToTheQueueButTerminalFailuresDoNot() {
		Fixture fixture = fixture();
		OffsetDateTime now = OffsetDateTime.now();
		var state = fixture.repository().upsertDesiredState(
				fixture.connection(), fixture.clientId(), "[]", "REVOKE", "PENDING",
				null, null, "membership:expired:1", null, now);
		var job = fixture.repository().enqueueJob(
				fixture.connection(), state, "person-42", "job-expired-1", "MEMBERSHIP_EVENT", now);
		var firstClaim = fixture.repository().claimJobs(
				fixture.connector(), 1, now.plusSeconds(1), now.plusSeconds(31));
		assertThat(firstClaim).hasSize(1);
		fixture.repository().failJob(
				fixture.connector(), job.id(), firstClaim.getFirst().claimToken(),
				"PROVIDER_TEMPORARILY_UNAVAILABLE", true,
				now.plusSeconds(10), now.plusSeconds(2));
		assertThat(fixture.repository().claimJobs(
				fixture.connector(), 1, now.plusSeconds(9), now.plusSeconds(39))).isEmpty();
		var retryClaim = fixture.repository().claimJobs(
				fixture.connector(), 1, now.plusSeconds(11), now.plusSeconds(41));
		assertThat(retryClaim).hasSize(1);
		fixture.repository().failJob(
				fixture.connector(), job.id(), retryClaim.getFirst().claimToken(),
				"EXTERNAL_PERSON_REJECTED", false,
				now.plusSeconds(12), now.plusSeconds(12));
		assertThat(fixture.repository().claimJobs(
				fixture.connector(), 1, now.plusDays(1), now.plusDays(1).plusMinutes(1))).isEmpty();
	}

	@Test
	void rotatingConnectorKeyClearsRuntimeStateAndReportedCapabilities() {
		Fixture fixture = fixture();
		OffsetDateTime now = OffsetDateTime.now();
		fixture.jdbc().sql("""
				UPDATE access_provider_connection
				SET last_heartbeat_at = :now,
				    last_synchronized_at = :now,
				    connector_reported_status = 'HEALTHY',
				    connector_capabilities = '["EVENT_POLLING", "REMOTE_OPEN"]'::jsonb,
				    last_error = 'stale error'
				WHERE id = :connectionId
				""")
				.param("now", now)
				.param("connectionId", fixture.connection().id())
				.update();

		var accessRepository = new AccessRepository(fixture.jdbc());
		accessRepository.upsertPhysicalProviderConnection(
				fixture.organizationId(), fixture.branchId(), "HIKCENTRAL", "HikCentral",
				"rotated-" + fixture.connection().id(), true, null);

		var runtimeState = fixture.jdbc().sql("""
				SELECT last_heartbeat_at, last_synchronized_at, connector_reported_status,
				       connector_capabilities::text AS connector_capabilities, last_error
				FROM access_provider_connection
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				""")
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.query((rs, rowNumber) -> new Object[] {
						rs.getObject("last_heartbeat_at"),
						rs.getObject("last_synchronized_at"),
						rs.getObject("connector_reported_status"),
						rs.getString("connector_capabilities"),
						rs.getObject("last_error")})
				.single();

		assertThat(runtimeState).containsExactly(null, null, null, "[]", null);
	}

	@Test
	void physicalBenefitAuthorizationModesRemainTenantAndBranchScoped() {
		Fixture fixture = fixture();
		Fixture otherTenant = fixture();
		UUID benefitId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO benefit (
				    id, organization_id, branch_id, code, name, benefit_type, discount_type,
				    requires_evidence, combinable, priority, status, access_scope,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :code, 'Acceso General', 'ACCESS', 'NONE',
				        FALSE, FALSE, 100, 'ACTIVE', 'LOCAL', now(), now(), 0)
				""")
				.param("id", benefitId)
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("code", "ACCESS-" + benefitId)
				.update();
		UUID providerDeviceId = insertDevice(fixture, "PROVIDER");
		UUID onlineDeviceId = insertDevice(fixture, "ONLINE");
		for (UUID deviceId : List.of(providerDeviceId, onlineDeviceId)) {
			fixture.jdbc().sql("""
					INSERT INTO access_device_benefit (
					    access_device_id, benefit_id, organization_id, created_at
					)
					VALUES (:deviceId, :benefitId, :organizationId, now())
					""")
					.param("deviceId", deviceId)
					.param("benefitId", benefitId)
					.param("organizationId", fixture.organizationId())
					.update();
		}
		insertDeviceLink(fixture, providerDeviceId, "door-provider", "PROVIDER_MANAGED");
		insertDeviceLink(fixture, onlineDeviceId, "door-online", "GYMFORT_ONLINE");

		var repository = new AccessRepository(fixture.jdbc());
		assertThat(repository.physicalBenefitAuthorizationModes(
				fixture.organizationId(), fixture.branchId(), fixture.connection().id()))
				.extracting(AccessRepository.PhysicalBenefitAuthorizationModeRecord::authorizationMode)
				.containsExactly("GYMFORT_ONLINE", "PROVIDER_MANAGED");
		assertThat(repository.physicalBenefitAuthorizationModes(
				otherTenant.organizationId(), otherTenant.branchId(), otherTenant.connection().id()))
				.isEmpty();
	}

	@Test
	void supersededDesiredStateWaitsForTheActiveLeaseAndCancelsTheOldAttempt() {
		Fixture fixture = fixture();
		OffsetDateTime now = OffsetDateTime.now();
		var oldState = fixture.repository().upsertDesiredState(
				fixture.connection(), fixture.clientId(), "[\"LEVEL-PREMIUM\"]", "UPSERT", "PENDING",
				null, null, "membership:premium", null, now);
		var oldJob = fixture.repository().enqueueJob(
				fixture.connection(), oldState, "person-42", "job-premium", "MEMBERSHIP_EVENT", now);
		var oldClaim = fixture.repository().claimJobs(
				fixture.connector(), 1, now.plusSeconds(1), now.plusSeconds(61)).getFirst();

		var newState = fixture.repository().upsertDesiredState(
				fixture.connection(), fixture.clientId(), "[\"LEVEL-BASIC\"]", "UPSERT", "PENDING",
				null, null, "membership:basic", null, now.plusSeconds(2));
		var newJob = fixture.repository().enqueueJob(
				fixture.connection(), newState, "person-42", "job-basic", "MEMBERSHIP_EVENT", now.plusSeconds(2));

		assertThat(fixture.repository().claimJobs(
				fixture.connector(), 10, now.plusSeconds(3), now.plusSeconds(63))).isEmpty();
		assertThat(fixture.repository().extendLease(
				fixture.connector(), oldJob.id(), oldClaim.claimToken(),
				now.plusSeconds(3), now.plusSeconds(93))).isFalse();
		assertThat(fixture.repository().failJob(
				fixture.connector(), oldJob.id(), oldClaim.claimToken(), "SUPERSEDED", true,
				now.plusSeconds(4), now.plusSeconds(4)))
				.get().extracting(PhysicalAccessRepository.SyncJobRecord::status).isEqualTo("CANCELLED");

		var currentClaim = fixture.repository().claimJobs(
				fixture.connector(), 10, now.plusSeconds(5), now.plusSeconds(65));
		assertThat(currentClaim).singleElement().extracting(PhysicalAccessRepository.SyncJobRecord::id)
				.isEqualTo(newJob.id());
		fixture.repository().completeJob(
				fixture.connector(), newJob.id(), currentClaim.getFirst().claimToken(),
				"provider-operation-basic", now.plusSeconds(6));
		assertThat(fixture.repository().desiredState(
				fixture.organizationId(), fixture.branchId(), fixture.connection().id(), fixture.clientId()))
				.get().satisfies(state -> {
					assertThat(state.status()).isEqualTo("SYNCED");
					assertThat(state.desiredAccessLevelRefsJson()).isEqualTo("[\"LEVEL-BASIC\"]");
				});
	}

	@Test
	void physicalEventsAreIdempotentAndDeviceLinksCannotCrossBranches() {
		Fixture fixture = fixture();
		UUID deviceId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO access_device (
				    id, organization_id, branch_id, name, device_code, device_type, status,
				    provider, adapter_config_json, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, 'Torniquete', :code, 'TURNSTILE', 'ACTIVE',
				        'HIKCENTRAL', '{}'::jsonb, now(), now(), 0)
				""")
				.param("id", deviceId)
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("code", "TURN-" + deviceId)
				.update();
		fixture.repository().upsertDeviceLink(
				fixture.connection(), deviceId, "door-main", "Entrada principal", null, OffsetDateTime.now());

		UUID correlationId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		var first = fixture.repository().insertExternalEvent(
				fixture.connector(), deviceId, fixture.clientId(), "HIKCENTRAL", "event-100",
				"person-42", "door-main", "ACCESS_ATTEMPT", "{}", "GRANTED", "PROVIDER_GRANTED",
				now, correlationId, now);
		var duplicate = fixture.repository().insertExternalEvent(
				fixture.connector(), deviceId, null, "HIKCENTRAL", "event-100",
				"person-other", "door-main", "ACCESS_ATTEMPT", "{}", "DENIED", "ALTERED_RETRY",
				now, correlationId, now.plusSeconds(1));
		assertThat(first.created()).isTrue();
		assertThat(duplicate.created()).isFalse();
		assertThat(duplicate.accessLogId()).isEqualTo(first.accessLogId());
		assertThat(duplicate.clientId()).isEqualTo(fixture.clientId());
		assertThat(duplicate.reason()).isEqualTo("PROVIDER_GRANTED");

		UUID otherBranchId = UUID.randomUUID();
		insertBranch(fixture.jdbc(), fixture.organizationId(), otherBranchId, "OTHER-" + otherBranchId);
		UUID otherDeviceId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO access_device (
				    id, organization_id, branch_id, name, device_code, device_type, status,
				    provider, adapter_config_json, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, 'Otra puerta', :code, 'DOOR', 'ACTIVE',
				        'HIKCENTRAL', '{}'::jsonb, now(), now(), 0)
				""")
				.param("id", otherDeviceId)
				.param("organizationId", fixture.organizationId())
				.param("branchId", otherBranchId)
				.param("code", "DOOR-" + otherDeviceId)
				.update();

		assertThatThrownBy(() -> fixture.repository().upsertDeviceLink(
				fixture.connection(), otherDeviceId, "door-foreign", "Otra", null, OffsetDateTime.now()))
				.hasRootCauseInstanceOf(SQLException.class);
	}

	@Test
	void onlineAuthorizationsAreIdempotentAndActuationDoesNotCreateAnotherAccessDecision() {
		Fixture fixture = fixture();
		UUID deviceId = insertDevice(fixture, "ONLINE");
		var link = fixture.repository().upsertDeviceLink(
				fixture.connection(), deviceId, "door-online", "Torniquete online",
				"GYMFORT_ONLINE", null, OffsetDateTime.now());
		OffsetDateTime now = OffsetDateTime.now();
		var first = fixture.repository().lockOrCreateAuthorization(
				fixture.connector(), link, fixture.clientId(), "online-event-1", "person-42",
				"door-online", null, now, now);
		var duplicate = fixture.repository().lockOrCreateAuthorization(
				fixture.connector(), link, fixture.clientId(), "online-event-1", "person-altered",
				"door-online", "DENIED", now.plusSeconds(1), now.plusSeconds(1));
		assertThat(duplicate.id()).isEqualTo(first.id());
		assertThat(duplicate.externalPersonRef()).isEqualTo("person-42");
		assertThat(duplicate.authorizationMode()).isEqualTo("GYMFORT_ONLINE");

		UUID logId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO access_log (
				    id, organization_id, branch_id, device_id, client_id,
				    access_type, decision, source, reason, command_status,
				    occurred_at, created_at, correlation_id
				) VALUES (
				    :id, :organizationId, :branchId, :deviceId, :clientId,
				    'MEMBERSHIP_CHECK', 'GRANTED', 'CONNECTOR', 'OK', 'REMOTE_OPEN_PENDING',
				    :now, :now, :correlationId
				)
				""")
				.param("id", logId)
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("deviceId", deviceId)
				.param("clientId", fixture.clientId())
				.param("now", now)
				.param("correlationId", UUID.randomUUID())
				.update();
		var decided = fixture.repository().completeAuthorization(
				fixture.connector(), first.id(), logId, "GRANTED", "PENDING", now);
		var actuated = fixture.repository().reportActuation(
				fixture.connector(), decided.id(), "SUCCEEDED", "hcp:open:1", null, now.plusSeconds(1))
				.orElseThrow();

		assertThat(actuated.actuationStatus()).isEqualTo("SUCCEEDED");
		assertThat(actuated.externalOperationRef()).isEqualTo("hcp:open:1");
		assertThat(fixture.repository().reportActuation(
				fixture.connector(), decided.id(), "SUCCEEDED", "hcp:open:1", null, now.plusSeconds(2)))
				.isPresent();
		assertThat(fixture.repository().reportActuation(
				fixture.connector(), decided.id(), "FAILED", null, "late contradictory result",
				now.plusSeconds(3))).isEmpty();
		assertThat(fixture.jdbc().sql("SELECT COUNT(*) FROM access_log WHERE id = :id")
				.param("id", logId).query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void discoveredDevicesRequireAnExplicitTenantScopedLink() {
		Fixture fixture = fixture();
		var candidate = fixture.repository().upsertDeviceCandidate(
				fixture.connector(), "discovered-door", "Puerta detectada", "DOOR",
				"{\"model\":\"test\"}", OffsetDateTime.now());
		assertThat(candidate.status()).isEqualTo("UNLINKED");

		UUID deviceId = insertDevice(fixture, "DISCOVERED");
		fixture.repository().upsertDeviceLink(
				fixture.connection(), deviceId, "discovered-door", "Puerta detectada",
				"PROVIDER_MANAGED", null, OffsetDateTime.now());
		assertThat(fixture.repository().deviceCandidates(
				fixture.organizationId(), fixture.branchId(), fixture.connection().id(), 20))
				.singleElement()
				.satisfies(found -> {
					assertThat(found.status()).isEqualTo("LINKED");
					assertThat(found.linkedAccessDeviceId()).isEqualTo(deviceId);
				});
	}

	@Test
	void opaquePersonAndDeviceReferencesCannotBeLinkedTwice() {
		Fixture fixture = fixture();
		UUID secondClientId = insertClient(
				fixture.jdbc(), fixture.organizationId(), fixture.branchId(), "Second", "Member");
		fixture.jdbc().sql("""
				INSERT INTO access_provider_client_link (
				    id, organization_id, branch_id, connection_id, client_id,
				    external_person_ref, linked_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :connectionId, :clientId,
				        'person-shared', now(), now(), 0)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("connectionId", fixture.connection().id())
				.param("clientId", fixture.clientId())
				.update();

		assertThatThrownBy(() -> fixture.jdbc().sql("""
				INSERT INTO access_provider_client_link (
				    id, organization_id, branch_id, connection_id, client_id,
				    external_person_ref, linked_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :connectionId, :clientId,
				        'person-shared', now(), now(), 0)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("connectionId", fixture.connection().id())
				.param("clientId", secondClientId)
				.update()).hasRootCauseInstanceOf(SQLException.class);

		UUID firstDeviceId = insertDevice(fixture, "FIRST");
		UUID secondDeviceId = insertDevice(fixture, "SECOND");
		fixture.repository().upsertDeviceLink(
				fixture.connection(), firstDeviceId, "door-shared", null, null, OffsetDateTime.now());
		assertThatThrownBy(() -> fixture.repository().upsertDeviceLink(
				fixture.connection(), secondDeviceId, "door-shared", null, null, OffsetDateTime.now()))
				.hasRootCauseInstanceOf(SQLException.class);
	}

	@Test
	void compositeTenantConstraintRejectsAClientFromAnotherOrganization() {
		Fixture fixture = fixture();
		UUID foreignOrganizationId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency,
				    created_at, updated_at, version
				)
				VALUES (:id, 'Foreign Legal', 'Foreign', 'ACTIVE',
				        'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", foreignOrganizationId).update();
		insertBranch(fixture.jdbc(), foreignOrganizationId, foreignBranchId, "FOREIGN");
		UUID foreignClientId = insertClient(
				fixture.jdbc(), foreignOrganizationId, foreignBranchId, "Foreign", "Member");

		assertThatThrownBy(() -> fixture.jdbc().sql("""
				INSERT INTO access_provider_client_link (
				    id, organization_id, branch_id, connection_id, client_id,
				    external_person_ref, linked_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :connectionId, :clientId,
				        'foreign-person', now(), now(), 0)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("connectionId", fixture.connection().id())
				.param("clientId", foreignClientId)
				.update()).hasRootCauseInstanceOf(SQLException.class);
	}

	@Test
	void credentialAuthenticationTimestampOnlyWritesAfterTheConfiguredWindow() {
		Fixture fixture = fixture();
		UUID credentialId = fixture.connector().credentialId();
		OffsetDateTime firstAuthentication = OffsetDateTime.now().withNano(0);

		assertThat(fixture.repository().touchCredentialIfStale(
				credentialId, firstAuthentication, firstAuthentication.minusMinutes(5))).isTrue();
		assertThat(credentialVersion(fixture.jdbc(), credentialId)).isEqualTo(1);
		assertThat(credentialLastAuthenticatedAt(fixture.jdbc(), credentialId))
				.isEqualTo(firstAuthentication);

		OffsetDateTime insideWindow = firstAuthentication.plusMinutes(1);
		assertThat(fixture.repository().touchCredentialIfStale(
				credentialId, insideWindow, insideWindow.minusMinutes(5))).isFalse();
		assertThat(credentialVersion(fixture.jdbc(), credentialId)).isEqualTo(1);
		assertThat(credentialLastAuthenticatedAt(fixture.jdbc(), credentialId))
				.isEqualTo(firstAuthentication);

		OffsetDateTime afterWindow = firstAuthentication.plusMinutes(6);
		assertThat(fixture.repository().touchCredentialIfStale(
				credentialId, afterWindow, afterWindow.minusMinutes(5))).isTrue();
		assertThat(credentialVersion(fixture.jdbc(), credentialId)).isEqualTo(2);
		assertThat(credentialLastAuthenticatedAt(fixture.jdbc(), credentialId))
				.isEqualTo(afterWindow);
	}

	private int credentialVersion(JdbcClient jdbc, UUID credentialId) {
		return jdbc.sql("SELECT version FROM access_connector_credential WHERE id = :id")
				.param("id", credentialId)
				.query(Integer.class)
				.single();
	}

	private OffsetDateTime credentialLastAuthenticatedAt(JdbcClient jdbc, UUID credentialId) {
		return jdbc.sql("SELECT last_authenticated_at FROM access_connector_credential WHERE id = :id")
				.param("id", credentialId)
				.query(OffsetDateTime.class)
				.single();
	}

	private Fixture fixture() {
		JdbcClient jdbc = JdbcClient.create(dataSource());
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID connectionId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency,
				    created_at, updated_at, version
				)
				VALUES (:id, 'Physical Test Legal', 'Physical Test', 'ACTIVE',
				        'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).update();
		insertBranch(jdbc, organizationId, branchId, "MAIN-" + branchId);
		jdbc.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, email, phone, status,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, 'Andrea', 'Socia', :email, :phone, 'ACTIVE',
				        now(), now(), 0)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("email", "andrea-" + personId + "@example.com")
				.param("phone", "+52" + Math.abs(personId.hashCode()))
				.update();
		jdbc.sql("""
				INSERT INTO client (
				    id, organization_id, person_id, status, joined_at,
				    created_at, updated_at, version, home_branch_id
				)
				VALUES (:id, :organizationId, :personId, 'ACTIVE', now(), now(), now(), 0, :branchId)
				""")
				.param("id", clientId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("branchId", branchId)
				.update();
		jdbc.sql("""
				INSERT INTO access_provider_connection (
				    id, organization_id, branch_id, provider_key, display_name, connector_key,
				    enabled, sync_status, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, 'HIKCENTRAL', 'HikCentral', :connectorKey,
				        TRUE, 'AWAITING_CONNECTOR', now(), now(), 0)
				""")
				.param("id", connectionId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectorKey", "connector-" + connectionId)
				.update();
		PhysicalAccessRepository repository = new PhysicalAccessRepository(jdbc);
		ConnectionRecord connection = repository.connection(organizationId, branchId, connectionId).orElseThrow();
		var credential = repository.createCredential(connection, "hashed-test-secret", null, OffsetDateTime.now());
		return new Fixture(
				jdbc, repository, organizationId, branchId, clientId, connection,
				new ConnectorContext(
						credential.id(), organizationId, branchId, connectionId, "HIKCENTRAL",
						connection.connectorKey()));
	}

	private PhysicalAccessRepository repository() {
		return new PhysicalAccessRepository(JdbcClient.create(dataSource()));
	}

	private UUID insertClient(
			JdbcClient jdbc, UUID organizationId, UUID branchId, String firstName, String lastName
	) {
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, email, phone, status,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :firstName, :lastName, :email, :phone, 'ACTIVE',
				        now(), now(), 0)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("firstName", firstName)
				.param("lastName", lastName)
				.param("email", "physical-" + personId + "@example.com")
				.param("phone", "+521" + Math.abs(personId.hashCode()))
				.update();
		jdbc.sql("""
				INSERT INTO client (
				    id, organization_id, person_id, status, joined_at,
				    created_at, updated_at, version, home_branch_id
				)
				VALUES (:id, :organizationId, :personId, 'ACTIVE', now(), now(), now(), 0, :branchId)
				""")
				.param("id", clientId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("branchId", branchId)
				.update();
		return clientId;
	}

	private UUID insertDevice(Fixture fixture, String suffix) {
		UUID deviceId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO access_device (
				    id, organization_id, branch_id, name, device_code, device_type, status,
				    provider, adapter_config_json, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :name, :code, 'TURNSTILE', 'ACTIVE',
				        'HIKCENTRAL', '{}'::jsonb, now(), now(), 0)
				""")
				.param("id", deviceId)
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("name", "Turnstile " + suffix)
				.param("code", suffix + "-" + deviceId)
				.update();
		return deviceId;
	}

	private void insertDeviceLink(Fixture fixture, UUID deviceId, String externalRef, String mode) {
		fixture.jdbc().sql("""
				INSERT INTO access_provider_device_link (
				    id, organization_id, branch_id, connection_id, access_device_id,
				    external_device_ref, authorization_mode, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :branchId, :connectionId, :deviceId,
				        :externalRef, :mode, now(), now(), 0)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", fixture.organizationId())
				.param("branchId", fixture.branchId())
				.param("connectionId", fixture.connection().id())
				.param("deviceId", deviceId)
				.param("externalRef", externalRef)
				.param("mode", mode)
				.update();
	}

	private DriverManagerDataSource dataSource() {
		return new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private void insertBranch(JdbcClient jdbc, UUID organizationId, UUID branchId, String code) {
		jdbc.sql("""
				INSERT INTO branch (
				    id, organization_id, name, code, timezone, status, created_at, updated_at, version
				)
				VALUES (:id, :organizationId, 'Main', :code, 'America/Mexico_City',
				        'ACTIVE', now(), now(), 0)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.param("code", code.substring(0, Math.min(code.length(), 32)))
				.update();
	}

	private record Fixture(
			JdbcClient jdbc,
			PhysicalAccessRepository repository,
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			ConnectionRecord connection,
			ConnectorContext connector
	) {
	}
}
