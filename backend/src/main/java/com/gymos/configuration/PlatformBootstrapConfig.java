package com.gymos.configuration;

import com.gymos.identity.api.operations.PlatformAdministratorBootstrap;
import com.gymos.identity.api.operations.PlatformAdministratorBootstrap.BootstrapCommand;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformBootstrapProperties.class)
public class PlatformBootstrapConfig {
	@Bean
	@ConditionalOnProperty(prefix = "gymos.platform-bootstrap", name = "enabled", havingValue = "true")
	ApplicationRunner platformBootstrapRunner(
			PlatformAdministratorBootstrap service,
			PlatformBootstrapProperties properties
	) {
		return arguments -> service.bootstrap(new BootstrapCommand(
				properties.enabled(), properties.email(), properties.password(), properties.name()));
	}
}
