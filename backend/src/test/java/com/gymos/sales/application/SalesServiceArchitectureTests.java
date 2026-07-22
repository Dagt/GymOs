package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.application.result.SalesReportResult;
import com.gymos.sales.infrastructure.SalesRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class SalesServiceArchitectureTests {
	@Test
	void exposesOnlyDependencyInjectedConstructorWithoutJdbcClient() {
		var constructors = SalesService.class.getDeclaredConstructors();

		assertThat(constructors).hasSize(1);
		assertThat(Arrays.asList(constructors[0].getParameterTypes()))
				.doesNotContain(JdbcClient.class);
		assertThat(Arrays.asList(SalesService.class.getDeclaredFields()))
				.noneMatch(field -> field.getType().equals(JdbcClient.class));
	}

	@Test
	void repositoryDoesNotDeclarePublicContractModels() {
		assertThat(SalesRepository.class.getDeclaredClasses()).isEmpty();
	}

	@Test
	void applicationResultsDoNotExposeInfrastructureTypes() {
		assertThat(Arrays.stream(SaleDetailResult.class.getRecordComponents())
				.map(component -> component.getType().getPackageName())
				.toList())
				.noneMatch(packageName -> packageName.startsWith("com.gymos.sales.infrastructure"));
		assertThat(Arrays.stream(SalesReportResult.class.getRecordComponents())
				.map(component -> component.getType().getPackageName())
				.toList())
				.noneMatch(packageName -> packageName.startsWith("com.gymos.sales.infrastructure"));
	}

	@Test
	void salesSourcesDoNotImportNestedRepositoryModels() throws IOException {
		Path salesSource = Path.of("src", "main", "java", "com", "gymos", "sales");
		try (var files = Files.walk(salesSource)) {
			assertThat(files
					.filter(path -> path.toString().endsWith(".java"))
					.filter(this::containsNestedRepositoryImport)
					.toList())
					.isEmpty();
		}
	}

	private boolean containsNestedRepositoryImport(Path path) {
		try {
			return Files.readString(path).contains("SalesRepository.");
		} catch (IOException exception) {
			throw new IllegalStateException("No fue posible inspeccionar " + path, exception);
		}
	}
}
