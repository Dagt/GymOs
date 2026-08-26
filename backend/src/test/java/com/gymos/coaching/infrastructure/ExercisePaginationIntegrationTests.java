package com.gymos.coaching.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
class ExercisePaginationIntegrationTests {
	@Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
	private static JdbcClient jdbc;

	@BeforeAll
	static void migrate() {
		var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(source);
	}

	@Test
	void serverSearchPagesAllExercisesAndMatchesEquipmentMuscleAndAliasesAcrossPages() {
		UUID organization = organization();
		UUID other = organization();
		var repository = new CoachingCatalogRepository(jdbc);
		List<UUID> expected = new ArrayList<>();
		for (int index = 0; index < 127; index++) {
			UUID id = UUID.randomUUID();
			expected.add(id);
			jdbc.sql("""
					INSERT INTO exercise (id,organization_id,name,normalized_name,exercise_type,origin,primary_muscle_group,equipment)
					VALUES (:id,:org,:name,:normalized,'STRENGTH','ORGANIZATION',:muscle,:equipment)
					""").param("id", id).param("org", organization)
					.param("name", "QA paginación " + String.format("%03d", index))
					.param("normalized", "qa paginacion " + index)
					.param("muscle", index == 126 ? "Músculo excepcional" : "General")
					.param("equipment", index == 126 ? "Equipo excepcional" : "Sin equipo").update();
		}
		jdbc.sql("INSERT INTO exercise (id,organization_id,name,normalized_name,exercise_type,origin) VALUES (:id,:org,'QA paginación ajena','qa paginacion ajena','STRENGTH','ORGANIZATION')")
				.param("id", UUID.randomUUID()).param("org", other).update();
		List<UUID> visited = new ArrayList<>();
		for (int page = 0; page < 6; page++) {
			var result = repository.exercises(organization, "QA paginación", null, null, null, page, 25);
			assertThat(result.totalElements()).isEqualTo(127);
			assertThat(result.items()).hasSize(page == 5 ? 2 : 25);
			visited.addAll(result.items().stream().map(CoachingCatalogRepository.ExerciseRecord::id).toList());
		}
		assertThat(visited).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
		for (String query : List.of("QA paginación 126", "Equipo excepcional", "Músculo excepcional")) {
			var result = repository.exercises(organization, query, null, null, null, 0, 25);
			assertThat(result.totalElements()).isEqualTo(1);
			assertThat(result.items()).extracting(CoachingCatalogRepository.ExerciseRecord::id).containsExactly(expected.getLast());
		}
		jdbc.sql("INSERT INTO exercise_alias (id,exercise_id,alias,normalized_alias) VALUES (:id,:exercise,'Alias remoto','alias remoto')")
				.param("id", UUID.randomUUID()).param("exercise", expected.getLast()).update();
		assertThat(repository.exercises(organization, "alias remoto", null, null, null, 0, 25).totalElements()).isEqualTo(1);
		assertThat(repository.exercises(other, "Equipo excepcional", null, null, null, 0, 25).totalElements()).isZero();
	}

	private UUID organization() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO organization (id,legal_name,trade_name,status,timezone,default_currency,created_at,updated_at,version) VALUES (:id,'Pagination','Pagination','ACTIVE','America/Mexico_City','MXN',now(),now(),0)")
				.param("id", id).update();
		return id;
	}
}
