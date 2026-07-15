package com.gymos.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
	@Bean
	OpenAPI gymOsOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("GymOS API")
						.version("v1")
						.description("API modular para la operación integral de gimnasios y centros de bienestar.")
						.license(new License().name("Proprietary")));
	}
}
