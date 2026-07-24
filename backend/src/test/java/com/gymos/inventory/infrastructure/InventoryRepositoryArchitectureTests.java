package com.gymos.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.catalog.api.CatalogProductOperations;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class InventoryRepositoryArchitectureTests {
	@Test
	void inventoryRepositoryRemainsSqlFreeCompatibilityFacade() throws Exception {
		assertThat(Arrays.stream(InventoryRepository.class.getDeclaredFields())
				.map(Field::getType)
				.toList())
				.containsExactlyInAnyOrder(
						InventoryStockQueryRepository.class,
						InventoryMovementQueryRepository.class,
						InventoryStockCommandRepository.class,
						InventoryMovementCommandRepository.class,
						InventorySaleDeductionRepository.class,
						CatalogProductOperations.class
				)
				.doesNotContain(JdbcClient.class);

		String source = Files.readString(Path.of("src/main/java/com/gymos/inventory/infrastructure/InventoryRepository.java"));
		assertThat(source)
				.doesNotContain("JdbcClient")
				.doesNotContain("SELECT ")
				.doesNotContain("INSERT ")
				.doesNotContain("UPDATE ");
	}
}
