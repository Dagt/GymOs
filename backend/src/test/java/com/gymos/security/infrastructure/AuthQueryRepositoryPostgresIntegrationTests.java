package com.gymos.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import com.gymos.security.application.GymOsUserDetailsService;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class AuthQueryRepositoryPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private AuthQueryRepository repository;
	private BCryptPasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		repository = new AuthQueryRepository(jdbcClient);
		passwordEncoder = new BCryptPasswordEncoder(4);
	}

	@Test
	void activeTenantUserCanBeResolvedByUsernameOrUniqueEmail() {
		String email = "recepcion@example.com";
		String password = "Temporal123!";
		UUID userId = createTenantUser("r", email, password);

		var byUsername = repository.findActiveUserByLoginIdentifier("r");
		var byEmail = repository.findActiveUserByLoginIdentifier("  RECEPCION@EXAMPLE.COM  ");

		assertThat(byUsername).isPresent().get().extracting(AuthQueryRepository.AuthenticatedUser::userId)
				.isEqualTo(userId);
		assertThat(byEmail).isPresent().get().satisfies(user -> {
			assertThat(user.userId()).isEqualTo(userId);
			assertThat(user.username()).isEqualTo("r");
			assertThat(passwordEncoder.matches(password, user.passwordHash())).isTrue();
		});
		var authenticationProvider = new DaoAuthenticationProvider(new GymOsUserDetailsService(repository));
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		assertThatCode(() -> authenticationProvider.authenticate(
				new UsernamePasswordAuthenticationToken(email, password)))
				.doesNotThrowAnyException();
	}

	@Test
	void tenantScopedDuplicateEmailIsRejectedAsAnAmbiguousLoginIdentifier() {
		String sharedEmail = "coach.shared@example.com";
		UUID firstUserId = createTenantUser("coach.norte", sharedEmail, "Temporal123!");
		UUID secondUserId = createTenantUser("coach.sur", sharedEmail, "Temporal456!");

		assertThat(repository.findActiveUserByLoginIdentifier(sharedEmail)).isEmpty();
		assertThat(repository.findActiveUserByLoginIdentifier("coach.norte"))
				.isPresent().get().extracting(AuthQueryRepository.AuthenticatedUser::userId)
				.isEqualTo(firstUserId);
		assertThat(repository.findActiveUserByLoginIdentifier("coach.sur"))
				.isPresent().get().extracting(AuthQueryRepository.AuthenticatedUser::userId)
				.isEqualTo(secondUserId);
	}

	private UUID createTenantUser(String username, String email, String password) {
		UUID organizationId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO organization (
				 id, legal_name, trade_name, tax_id, status, timezone, default_currency,
				 contact_email, created_at, updated_at, version
				) VALUES (
				 :id, 'Gimnasio Login SA', 'Gimnasio Login', :taxId, 'ACTIVE',
				 'America/Mexico_City', 'MXN', :email, now(), now(), 0
				)
				""")
				.param("id", organizationId)
				.param("taxId", "LOGIN" + organizationId.toString().replace("-", "").substring(0, 10))
				.param("email", email)
				.update();
		jdbcClient.sql("""
				INSERT INTO person (
				 id, organization_id, first_name, last_name, email, status,
				 created_at, updated_at, version
				) VALUES (
				 :id, :organizationId, 'Usuario', 'Prueba', :email, 'ACTIVE',
				 now(), now(), 0
				)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("email", email)
				.update();
		jdbcClient.sql("""
				INSERT INTO user_account (
				 id, organization_id, person_id, username, password_hash, status,
				 failed_attempts, created_at, updated_at, version
				) VALUES (
				 :id, :organizationId, :personId, :username, :passwordHash, 'ACTIVE',
				 0, now(), now(), 0
				)
				""")
				.param("id", userId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("username", username)
				.param("passwordHash", passwordEncoder.encode(password))
				.update();
		return userId;
	}
}
