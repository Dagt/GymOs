package com.gymos.support;

import com.gymos.common.security.GymOsPrincipal;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Isolated tenant fixtures for real PostgreSQL workflow tests; never uses demo/customer data. */
public final class OperationalTestData {
    public final JdbcClient jdbc;
    public final UUID organizationId = UUID.randomUUID();
    public final UUID branchId = UUID.randomUUID();
    public final UUID ownerId;
    public final GymOsPrincipal owner;

    public OperationalTestData(JdbcClient jdbc) {
        this.jdbc = jdbc;
        jdbc.sql("""
                INSERT INTO organization(id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at)
                VALUES (:id, 'Pruebas de operación', 'Pruebas', 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now())
                """).param("id", organizationId).update();
        jdbc.sql("""
                INSERT INTO branch(id, organization_id, name, code, timezone, status, created_at, updated_at)
                VALUES (:id, :org, 'Centro', 'CEN', 'America/Mexico_City', 'ACTIVE', now(), now())
                """).param("id", branchId).param("org", organizationId).update();
        ownerId = user("Dueno del gimnasio", "test-hash");
        owner = new GymOsPrincipal(ownerId, organizationId, branchId, "owner-test", "Dueño", Set.of(
                "user.manage", "benefit.manage", "workout.manage.all", "workout.read.all", "coach.metrics.read.all", "coach.metrics.read", "report.coaches.read"), Set.of("Dueno del gimnasio"), false);
    }

    public UUID person(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO person(id, organization_id, first_name, last_name, email, status, created_at, updated_at)
                VALUES (:id, :org, :name, 'Prueba', :email, 'ACTIVE', now(), now())
                """).param("id", id).param("org", organizationId).param("name", name).param("email", id + "@example.invalid").update();
        return id;
    }

    public UUID user(String role, String passwordHash) {
        UUID person = person(role);
        UUID id = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO user_account(id, organization_id, person_id, username, password_hash, status, account_scope, created_at, updated_at)
                VALUES (:id, :org, :person, :username, :hash, 'ACTIVE', 'TENANT', now(), now())
                """).param("id", id).param("org", organizationId).param("person", person).param("username", id.toString()).param("hash", passwordHash).update();
        jdbc.sql("INSERT INTO role(id, organization_id, name, created_at, updated_at) VALUES (:id, :org, :name, now(), now())")
                .param("id", roleId).param("org", organizationId).param("name", role).update();
        jdbc.sql("INSERT INTO user_role(user_id, role_id) VALUES (:user, :role)").param("user", id).param("role", roleId).update();
        jdbc.sql("INSERT INTO user_branch_access(user_id, branch_id, access_scope) VALUES (:user, :branch, 'BRANCH')")
                .param("user", id).param("branch", branchId).update();
        return id;
    }

    public UUID client() {
        UUID id = UUID.randomUUID();
        UUID person = person("Socio");
        jdbc.sql("""
                INSERT INTO client(id, organization_id, person_id, home_branch_id, status, joined_at, created_at, updated_at)
                VALUES (:id, :org, :person, :branch, 'ACTIVE', now(), now(), now())
                """).param("id", id).param("org", organizationId).param("person", person).param("branch", branchId).update();
        return id;
    }

    public UUID coach() {
        UUID user = user("Coach", "test-hash");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO coach(id, organization_id, branch_id, user_account_id, display_name, status, created_at, updated_at)
                VALUES (:id, :org, :branch, :user, 'Coach de prueba', 'ACTIVE', now(), now())
                """).param("id", id).param("org", organizationId).param("branch", branchId).param("user", user).update();
        return id;
    }

    public Workout workout(UUID coachId) {
        UUID id = UUID.randomUUID(), version = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO workout(id, organization_id, branch_id, coach_id, owner_user_id, title, level, status, created_at, updated_at)
                VALUES (:id, :org, :branch, :coach, :owner, 'Fuerza y movilidad', 'GENERAL', 'PUBLISHED', now(), now())
                """).param("id", id).param("org", organizationId).param("branch", branchId).param("coach", coachId).param("owner", ownerId).update();
        jdbc.sql("""
                INSERT INTO workout_version(id, organization_id, workout_id, version_number, content_json, status, created_at)
                VALUES (:id, :org, :workout, 1, '{"sessions":[]}'::jsonb, 'PUBLISHED', now())
                """).param("id", version).param("org", organizationId).param("workout", id).update();
        return new Workout(id, version);
    }

    public UUID plan(String name, int days) {
        UUID product = UUID.randomUUID(), plan = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO catalog_product(id, organization_id, sku, name, product_type, price_cents, currency, status, created_at, updated_at)
                VALUES (:id, :org, :sku, :name, 'MEMBERSHIP_PLAN', 10000, 'MXN', 'ACTIVE', now(), now())
                """).param("id", product).param("org", organizationId).param("sku", product.toString()).param("name", name).update();
        jdbc.sql("""
                INSERT INTO membership_plan(id, organization_id, product_id, duration_days, status, created_at, updated_at)
                VALUES (:id, :org, :product, :days, 'ACTIVE', now(), now())
                """).param("id", plan).param("org", organizationId).param("product", product).param("days", days).update();
        return plan;
    }

    public record Workout(UUID id, UUID versionId) {}
}
