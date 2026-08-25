package com.gymos.identity.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.identity.application.IdentityProperties;
import com.gymos.identity.application.UserInvitationService;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class TenantOwnerInvitationPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private IdentityProvisioningRepository repository;
	private TenantOwnerProvisioningAdapter service;
	private TransactionTemplate transaction;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		repository = new IdentityProvisioningRepository(jdbcClient);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		when(encoder.encode(any())).thenReturn("unusable-password-hash");
		service = new TenantOwnerProvisioningAdapter(
				repository, encoder, new IdentityProperties(72, 60, "https://gymfort.mx"));
		transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Test
	void expiredLinkIsReplacedAtomicallyAndOnlyHashesReachPersistence() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		createTenant(organizationId, branchId);
		OffsetDateTime now = OffsetDateTime.now();

		var initial = transaction.execute(status -> service.provisionOwner(
				new TenantOwnerProvisioning.ProvisionOwner(
						organizationId, branchId, actorUserId, "Ana", "Dueña",
						"ana@example.com", "+525512345678", now.minusHours(73))));
		assertThat(initial).isNotNull();
		jdbcClient.sql("""
				UPDATE user_invitation
				SET expires_at = :expiredAt, created_at = :createdAt
				WHERE id = :invitationId
				""")
				.param("expiredAt", now.minusMinutes(1))
				.param("createdAt", now.minusHours(73))
				.param("invitationId", initial.invitationId())
				.update();

		var replacement = transaction.execute(status -> service.reissueOwnerInvitation(
				new TenantOwnerProvisioning.ReissueOwnerInvitation(organizationId, actorUserId, now)));
		assertThat(replacement).isNotNull();
		service.recordEmailDelivery(replacement.invitationId(), "SENT", now.plusSeconds(1));

		assertThat(invitationStatus(initial.invitationId())).isEqualTo("EXPIRED");
		assertThat(invitationStatus(replacement.invitationId())).isEqualTo("PENDING");
		assertThat(pendingInvitationCount(replacement.userId())).isEqualTo(1);
		String replacementToken = token(replacement.invitationUrl());
		assertThat(tokenHash(replacement.invitationId()))
				.isEqualTo(UserInvitationService.sha256(replacementToken))
				.doesNotContain(replacementToken);

		var state = service.invitationStates(Set.of(organizationId), now.plusSeconds(2)).getFirst();
		assertThat(state.invitationId()).isEqualTo(replacement.invitationId());
		assertThat(state.invitationStatus()).isEqualTo("PENDING");
		assertThat(state.emailDeliveryStatus()).isEqualTo("SENT");
		assertThat(state.lastEmailSentAt()).isNotNull();
	}

	private void createTenant(UUID organizationId, UUID branchId) {
		jdbcClient.sql("""
				INSERT INTO organization (
				 id, legal_name, trade_name, tax_id, status, timezone, default_currency,
				 contact_email, contact_phone, created_at, updated_at, version
				) VALUES (
				 :id, 'Gimnasio Invitaciones SA', 'Gimnasio Invitaciones', :taxId, 'ACTIVE',
				 'America/Mexico_City', 'MXN', 'contacto@example.com', '+525512345678', now(), now(), 0
				)
				""")
				.param("id", organizationId)
				.param("taxId", "INV" + organizationId.toString().replace("-", "").substring(0, 10))
				.update();
		jdbcClient.sql("""
				INSERT INTO branch (
				 id, organization_id, name, code, timezone, status, phone,
				 created_at, updated_at, version
				) VALUES (
				 :id, :organizationId, 'Principal', 'MAIN', 'America/Mexico_City', 'ACTIVE',
				 '+525512345678', now(), now(), 0
				)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.update();
	}

	private String invitationStatus(UUID invitationId) {
		return jdbcClient.sql("SELECT status FROM user_invitation WHERE id = :id")
				.param("id", invitationId).query(String.class).single();
	}

	private int pendingInvitationCount(UUID userId) {
		return jdbcClient.sql("SELECT COUNT(*) FROM user_invitation WHERE user_id = :id AND status = 'PENDING'")
				.param("id", userId).query(Integer.class).single();
	}

	private String tokenHash(UUID invitationId) {
		return jdbcClient.sql("SELECT token_hash FROM user_invitation WHERE id = :id")
				.param("id", invitationId).query(String.class).single();
	}

	private String token(String invitationUrl) {
		return URI.create(invitationUrl).getQuery().substring("token=".length());
	}
}
