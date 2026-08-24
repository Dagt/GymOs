package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ActivationRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectorContext;
import com.gymos.access.infrastructure.PhysicalAccessRepository.CredentialRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.JobCounts;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.ApiAuthenticationException;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.InMemoryRateLimiter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PhysicalConnectorServiceTests {
	private final PhysicalAccessRepository repository = mock(PhysicalAccessRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final AuditService auditService = mock(AuditService.class);
	private final PhysicalConnectorSecurityAudit securityAudit = mock(PhysicalConnectorSecurityAudit.class);
	private final PhysicalConnectorProperties properties = new PhysicalConnectorProperties(
			2, Duration.ofMinutes(5), 2, Duration.ofMinutes(2), Duration.ofMinutes(5));
	private final PhysicalConnectorService service = new PhysicalConnectorService(
			repository,
			mock(AccessRepository.class),
			mock(PhysicalAccessSynchronizationService.class),
			new PhysicalAccessProviderCatalog(),
			passwordEncoder,
			new ObjectMapper(),
			auditService,
			securityAudit,
			new InMemoryRateLimiter(),
			properties
	);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID connectionId = UUID.randomUUID();
	private final ConnectionRecord connection = new ConnectionRecord(
			connectionId, organizationId, branchId, "HIKCENTRAL", "HikCentral", "connector-main",
			true, "AWAITING_CONNECTOR", null, null, null, null, null, null, null);

	@BeforeEach
	void connectionExists() {
		when(repository.connection(organizationId, branchId)).thenReturn(Optional.of(connection));
		when(repository.connectionByConnectorKey("connector-main")).thenReturn(Optional.of(connection));
	}

	@Test
	void activationCodeIsShownOnceButOnlyItsHashIsPersisted() {
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "owner@gymos.local", "Owner",
				Set.of("access.physical.manage"));
		UUID activationId = UUID.randomUUID();
		when(repository.createActivation(
				eq(connection), any(), eq("ACTIVATION"), any(), eq(principal.userId()), any()))
				.thenReturn(new ActivationRecord(
						activationId, organizationId, branchId, connectionId, "stored-hash",
						"ACTIVATION", OffsetDateTime.now().plusMinutes(15), null, null));

		var result = service.issueActivation(principal, "ACTIVATION", "correlation");

		ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
		verify(repository).createActivation(
				eq(connection), storedHash.capture(), eq("ACTIVATION"), any(), eq(principal.userId()), any());
		assertThat(result.activationCode()).startsWith("gfa_");
		assertThat(storedHash.getValue()).isNotEqualTo(result.activationCode());
		assertThat(passwordEncoder.matches(result.activationCode(), storedHash.getValue())).isTrue();
	}

	@Test
	void activationExchangesTheOneTimeCodeForASecretThatIsAlsoStoredOnlyAsAHash() {
		String activationCode = "test-one-time-code";
		ActivationRecord activation = new ActivationRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode(activationCode), "ACTIVATION",
				OffsetDateTime.now().plusMinutes(10), null, null);
		when(repository.openActivationForUpdate(connectionId)).thenReturn(Optional.of(activation));
		when(repository.consumeActivation(eq(activation.id()), any())).thenReturn(true, false);
		when(repository.createCredential(eq(connection), any(), eq(null), any()))
				.thenAnswer(invocation -> new CredentialRecord(
						UUID.randomUUID(), organizationId, branchId, connectionId,
						invocation.getArgument(1), "ACTIVE", OffsetDateTime.now(), null, null));

		var result = service.activate(
				"connector-main", activationCode, "198.51.100.10", "correlation");

		ArgumentCaptor<String> secretHash = ArgumentCaptor.forClass(String.class);
		verify(repository).createCredential(eq(connection), secretHash.capture(), eq(null), any());
		assertThat(result.connectorSecret()).startsWith("gfs_");
		assertThat(secretHash.getValue()).isNotEqualTo(result.connectorSecret());
		assertThat(passwordEncoder.matches(result.connectorSecret(), secretHash.getValue())).isTrue();
		assertThatThrownBy(() -> service.activate(
				"connector-main", activationCode, "198.51.100.10", "second"))
				.isInstanceOf(ApiAuthenticationException.class);
	}

	@Test
	void revokedOrMissingCredentialIsRejectedAndAuditedWithoutJwt() {
		when(repository.activeCredential(connectionId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.authenticate(
				"connector-main", "gfs_revoked", "198.51.100.11", "correlation"))
				.isInstanceOf(ApiAuthenticationException.class);
		verify(securityAudit).authenticationRejected(
				organizationId, branchId, connectionId, "correlation");
	}

	@Test
	void validSecretAuthenticatesAndHeartbeatUpdatesSanitizedHealth() {
		String connectorSecret = "test-valid-connector-secret";
		CredentialRecord credential = new CredentialRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode(connectorSecret), "ACTIVE", OffsetDateTime.now(), null, null);
		when(repository.activeCredential(connectionId)).thenReturn(Optional.of(credential));
		when(repository.jobCounts(organizationId, branchId, connectionId)).thenReturn(new JobCounts(2, 1));

		ConnectorContext context = service.authenticate(
				"connector-main", connectorSecret, "198.51.100.12", "auth-correlation");
		var health = service.heartbeat(
				context, "HEALTHY", "1.2.3", "instance-main", "stale local error");

		assertThat(context.credentialId()).isEqualTo(credential.id());
		assertThat(health.status()).isEqualTo("HEALTHY");
		assertThat(health.pendingJobs()).isEqualTo(2);
		assertThat(health.failedJobs()).isEqualTo(1);
		ArgumentCaptor<OffsetDateTime> authenticatedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
		ArgumentCaptor<OffsetDateTime> staleBefore = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(repository).touchCredentialIfStale(
				eq(credential.id()), authenticatedAt.capture(), staleBefore.capture());
		assertThat(Duration.between(staleBefore.getValue(), authenticatedAt.getValue()))
				.isEqualTo(properties.lastAuthenticatedWriteInterval());
		verify(repository).updateHeartbeat(
				eq(context), eq("HEALTHY"), eq("1.2.3"), eq("instance-main"), eq(null), any());
	}

	@Test
	void connectorDiagnosticsRejectLanAddressesAndCredentialShapes() {
		ConnectorContext context = new ConnectorContext(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				"HIKCENTRAL", "connector-main");

		assertThatThrownBy(() -> service.heartbeat(
				context, "DEGRADED", "1.2.3", "instance-main",
				"Provider unavailable at http://10.0.0.15:443"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("CONNECTOR_DIAGNOSTIC_SENSITIVE"));
		assertThatThrownBy(() -> service.heartbeat(
				context, "DEGRADED", "1.2.3", "instance-main", "password=do-not-store"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("CONNECTOR_DIAGNOSTIC_SENSITIVE"));
	}

	@Test
	void invalidSecretIsRejectedEvenWhenAnActiveCredentialExists() {
		CredentialRecord credential = new CredentialRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode("test-correct-connector-secret"), "ACTIVE", OffsetDateTime.now(), null, null);
		when(repository.activeCredential(connectionId)).thenReturn(Optional.of(credential));

		assertThatThrownBy(() -> service.authenticate(
				"connector-main", "test-wrong-connector-secret", "198.51.100.13", "invalid-secret"))
				.isInstanceOf(ApiAuthenticationException.class);
		verify(securityAudit).authenticationRejected(
				organizationId, branchId, connectionId, "invalid-secret");
	}

	@Test
	void administrativeRevocationInvalidatesTheConnector() {
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "owner@gymos.local", "Owner",
				Set.of("access.physical.manage"));

		service.revoke(principal, "revoke-correlation");

		verify(repository).revokeOpenActivations(eq(connectionId), any());
		verify(repository).revokeActiveCredentials(eq(connectionId), any());
		verify(repository).markConnectionRevoked(eq(connectionId), any());
		when(repository.activeCredential(connectionId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.authenticate(
				"connector-main", "test-old-connector-secret", "198.51.100.14", "after-revoke"))
				.isInstanceOf(ApiAuthenticationException.class);
	}

	@Test
	void repeatedInvalidAuthenticationIsLimitedButAValidConnectorFromItsOwnOriginStillAuthenticates() {
		String validSecret = "test-valid-secret";
		CredentialRecord credential = new CredentialRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode(validSecret), "ACTIVE", OffsetDateTime.now(), null, null);
		when(repository.activeCredential(connectionId)).thenReturn(Optional.of(credential));

		for (int attempt = 0; attempt < properties.authenticationMaxFailures(); attempt++) {
			assertThatThrownBy(() -> service.authenticate(
					"connector-main", "wrong-secret", "203.0.113.50", "invalid"))
					.isInstanceOf(ApiAuthenticationException.class);
		}
		assertThatThrownBy(() -> service.authenticate(
				"connector-main", "wrong-secret", "203.0.113.50", "limited"))
				.isInstanceOfSatisfying(ApiAuthenticationException.class,
					exception -> assertThat(exception.code()).isEqualTo("CONNECTOR_AUTH_INVALID"));
		verify(repository, times(properties.authenticationMaxFailures()))
				.connectionByConnectorKey("connector-main");

		ConnectorContext authenticated = service.authenticate(
				"connector-main", validSecret, "203.0.113.51", "valid");
		assertThat(authenticated.connectionId()).isEqualTo(connectionId);
	}

	@Test
	void repeatedInvalidActivationCodesAreLimitedBeforeAnotherCredentialLookup() {
		ActivationRecord activation = new ActivationRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode("correct-code"), "ACTIVATION",
				OffsetDateTime.now().plusMinutes(10), null, null);
		when(repository.openActivationForUpdate(connectionId)).thenReturn(Optional.of(activation));

		for (int attempt = 0; attempt < properties.activationMaxFailures(); attempt++) {
			assertThatThrownBy(() -> service.activate(
					"connector-main", "wrong-code", "203.0.113.60", "invalid-activation"))
					.isInstanceOf(ApiAuthenticationException.class);
		}
		assertThatThrownBy(() -> service.activate(
				"connector-main", "wrong-code", "203.0.113.60", "limited-activation"))
				.isInstanceOfSatisfying(ApiAuthenticationException.class,
					exception -> assertThat(exception.code()).isEqualTo("CONNECTOR_AUTH_INVALID"));
		verify(repository, times(properties.activationMaxFailures()))
				.openActivationForUpdate(connectionId);
	}

	@Test
	void missingWrongAndRevokedCredentialsShareTheSamePublicFailureWithoutSecretMaterial() {
		String submittedSecret = "gfs_must-never-leak";
		CredentialRecord credential = new CredentialRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				passwordEncoder.encode("different-secret"), "ACTIVE", OffsetDateTime.now(), null, null);
		when(repository.activeCredential(connectionId)).thenReturn(Optional.of(credential));
		ApiAuthenticationException wrong = Assertions.assertThrows(
				ApiAuthenticationException.class,
				() -> service.authenticate(
						"connector-main", submittedSecret, "203.0.113.70", "wrong"));
		ApiAuthenticationException missing = Assertions.assertThrows(
				ApiAuthenticationException.class,
				() -> service.authenticate(
						"connector-unknown", submittedSecret, "203.0.113.71", "missing"));
		when(repository.activeCredential(connectionId)).thenReturn(Optional.empty());
		ApiAuthenticationException revoked = Assertions.assertThrows(
				ApiAuthenticationException.class,
				() -> service.authenticate(
						"connector-main", submittedSecret, "203.0.113.72", "revoked"));

		assertThat(java.util.List.of(wrong.code(), missing.code(), revoked.code()))
				.containsOnly("CONNECTOR_AUTH_INVALID");
		assertThat(java.util.List.of(
				wrong.publicMessage(), missing.publicMessage(), revoked.publicMessage()))
				.containsOnly(wrong.publicMessage())
				.allSatisfy(message -> assertThat(message)
						.doesNotContain(submittedSecret, "connector-main", "connector-unknown"));
	}

	@Test
	void physicalEventCannotResolveADeviceOutsideTheAuthenticatedConnection() {
		ConnectorContext context = new ConnectorContext(
				UUID.randomUUID(), organizationId, branchId, connectionId,
				"HIKCENTRAL", "connector-main");
		when(repository.deviceLinkByExternalRef(
				organizationId, branchId, connectionId, "door-from-another-branch"))
				.thenReturn(Optional.empty());
		var event = new PhysicalConnectorService.PhysicalEventInput(
				"HIKCENTRAL", "event-foreign", "person-42", "door-from-another-branch",
				OffsetDateTime.now(), "GRANTED", "ACCESS_ATTEMPT", null, java.util.Map.of());

		assertThatThrownBy(() -> service.ingestEvent(context, event, "event-correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_EVENT_DEVICE_UNRESOLVED"));
		verify(repository).deviceLinkByExternalRef(
				organizationId, branchId, connectionId, "door-from-another-branch");
	}
}
