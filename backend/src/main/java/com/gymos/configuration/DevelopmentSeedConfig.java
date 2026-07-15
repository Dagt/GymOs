package com.gymos.configuration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class DevelopmentSeedConfig {
	static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	static final UUID PERSON_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	static final UUID ROLE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@Bean
	@ConditionalOnProperty(prefix = "gymos.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
	ApplicationRunner developmentSeedRunner(DevelopmentSeedService service) {
		return arguments -> service.seed();
	}

	@Bean
	DevelopmentSeedService developmentSeedService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder) {
		return new DevelopmentSeedService(jdbcClient, passwordEncoder);
	}

	static class DevelopmentSeedService {
		private final JdbcClient jdbcClient;
		private final PasswordEncoder passwordEncoder;

		DevelopmentSeedService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder) {
			this.jdbcClient = jdbcClient;
			this.passwordEncoder = passwordEncoder;
		}

		@Transactional
		void seed() {
			OffsetDateTime now = OffsetDateTime.now();
			jdbcClient.sql("""
					INSERT INTO organization (id, legal_name, trade_name, tax_id, status, timezone, default_currency, contact_email, contact_phone, created_at, updated_at, version)
					VALUES (:id, 'GymOS Demo S.A. de C.V.', 'GymOS Demo', 'GYMOS-DEMO', 'ACTIVE', 'America/Mexico_City', 'MXN', 'admin@gymos.local', '+520000000000', :now, :now, 0)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", ORGANIZATION_ID)
					.param("now", now)
					.update();
			jdbcClient.sql("""
					INSERT INTO branch (id, organization_id, name, code, timezone, status, phone, created_at, updated_at, version)
					VALUES (:id, :organizationId, 'Sucursal Principal', 'MAIN', 'America/Mexico_City', 'ACTIVE', '+520000000000', :now, :now, 0)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", BRANCH_ID)
					.param("organizationId", ORGANIZATION_ID)
					.param("now", now)
					.update();
			jdbcClient.sql("""
					INSERT INTO person (id, organization_id, first_name, last_name, email, phone, status, created_at, updated_at, version)
					VALUES (:id, :organizationId, 'Admin', 'GymOS', 'admin@gymos.local', '+520000000001', 'ACTIVE', :now, :now, 0)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", PERSON_ID)
					.param("organizationId", ORGANIZATION_ID)
					.param("now", now)
					.update();
			jdbcClient.sql("""
					INSERT INTO user_account (id, organization_id, person_id, username, password_hash, status, failed_attempts, created_at, updated_at, version)
					VALUES (:id, :organizationId, :personId, 'admin@gymos.local', :passwordHash, 'ACTIVE', 0, :now, :now, 0)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", USER_ID)
					.param("organizationId", ORGANIZATION_ID)
					.param("personId", PERSON_ID)
					.param("passwordHash", passwordEncoder.encode("Admin123!"))
					.param("now", now)
					.update();
			jdbcClient.sql("""
					INSERT INTO role (id, organization_id, name, description, system_role, created_at, updated_at, version)
					VALUES (:id, :organizationId, 'Administrador', 'Administrador inicial de organizacion', true, :now, :now, 0)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", ROLE_ID)
					.param("organizationId", ORGANIZATION_ID)
					.param("now", now)
					.update();
			jdbcClient.sql("INSERT INTO user_role (user_id, role_id) VALUES (:userId, :roleId) ON CONFLICT DO NOTHING")
					.param("userId", USER_ID)
					.param("roleId", ROLE_ID)
					.update();
			jdbcClient.sql("INSERT INTO user_branch_access (user_id, branch_id, access_scope) VALUES (:userId, :branchId, 'BRANCH') ON CONFLICT DO NOTHING")
					.param("userId", USER_ID)
					.param("branchId", BRANCH_ID)
					.update();

			List<String> permissions = List.of(
					"organization.read",
					"organization.update",
					"branch.read",
					"branch.update",
					"client.create",
					"client.read",
					"client.update",
					"catalog.read",
					"catalog.manage",
					"sale.read",
					"sale.create",
					"sale.cancel",
					"sale.refund",
					"payment.read",
					"payment.manual.create",
					"payment.manual.validate",
					"payment.manual.reject",
					"payment.receipt.upload",
					"membership.read",
					"membership.renew",
					"membership.change-plan",
					"membership.cancel",
					"report.sales.read",
					"audit.read",
					"selfservice.terminal.manage"
			);
			for (int index = 0; index < permissions.size(); index++) {
				UUID permissionId = UUID.nameUUIDFromBytes(("gymos:" + permissions.get(index)).getBytes());
				jdbcClient.sql("""
						INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
						VALUES (:id, :code, :description, :module, :now, :now, 0)
						ON CONFLICT (code) DO NOTHING
						""")
						.param("id", permissionId)
						.param("code", permissions.get(index))
						.param("description", "Permiso " + permissions.get(index))
						.param("module", permissions.get(index).split("\\.")[0])
						.param("now", now)
						.update();
				jdbcClient.sql("INSERT INTO role_permission (role_id, permission_id) VALUES (:roleId, :permissionId) ON CONFLICT DO NOTHING")
						.param("roleId", ROLE_ID)
						.param("permissionId", permissionId)
						.update();
			}
		}
	}
}
