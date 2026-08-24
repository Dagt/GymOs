package com.gymos.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.common.security.StrongPasswordPolicy;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class DevelopmentSeedPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Test
	void explicitLocalSeedCanRecreateTheDemoTenantIdempotentlyAfterV88() {
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		var jdbcClient = JdbcClient.create(dataSource);
		var service = new DevelopmentSeedConfig.DevelopmentSeedService(
				jdbcClient, new BCryptPasswordEncoder(), new StrongPasswordPolicy());
		var properties = new SeedProperties(true, "local-owner@example.test", "LocalSeedOnly1!");

		assertThat(count(jdbcClient, "organization", DevelopmentSeedConfig.ORGANIZATION_ID)).isZero();
		service.seed(properties);
		service.seed(properties);

		assertThat(count(jdbcClient, "organization", DevelopmentSeedConfig.ORGANIZATION_ID)).isEqualTo(1);
		assertThat(count(jdbcClient, "branch", DevelopmentSeedConfig.BRANCH_ID)).isEqualTo(1);
		assertThat(jdbcClient.sql("""
				SELECT count(*)
				FROM user_account
				WHERE id = :id
				  AND account_scope = 'TENANT'
				  AND organization_id = :organizationId
				""")
				.param("id", DevelopmentSeedConfig.USER_ID)
				.param("organizationId", DevelopmentSeedConfig.ORGANIZATION_ID)
				.query(Integer.class).single()).isEqualTo(1);
	}

	private int count(JdbcClient jdbcClient, String table, UUID id) {
		return jdbcClient.sql("SELECT count(*) FROM " + table + " WHERE id = :id")
				.param("id", id)
				.query(Integer.class)
				.single();
	}
}
