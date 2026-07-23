package com.gymos.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CatalogRepositoryArchitectureTests {

	@Test
	void catalogRepositoryRemainsSqlFreeFacade() throws Exception {
		assertThat(Arrays.stream(CatalogRepository.class.getDeclaredFields())
				.map(Field::getType)
				.toList())
				.containsExactlyInAnyOrder(
						CatalogProductQueryRepository.class,
						CatalogPlanQueryRepository.class,
						CatalogSkuLookupRepository.class
				)
				.doesNotContain(JdbcClient.class);

		String source = Files.readString(Path.of("src/main/java/com/gymos/catalog/infrastructure/CatalogRepository.java"));

		assertThat(source)
				.doesNotContain("JdbcClient")
				.doesNotContain("SELECT ")
				.doesNotContain("INSERT ")
				.doesNotContain("UPDATE ");
	}
}
