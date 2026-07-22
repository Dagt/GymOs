package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

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
}
