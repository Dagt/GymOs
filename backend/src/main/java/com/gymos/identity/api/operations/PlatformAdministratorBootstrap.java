package com.gymos.identity.api.operations;

public interface PlatformAdministratorBootstrap {
	boolean bootstrap(BootstrapCommand command);

	record BootstrapCommand(
			boolean enabled,
			String email,
			String password,
			String name
	) {
	}
}
