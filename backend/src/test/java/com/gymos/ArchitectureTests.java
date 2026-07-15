package com.gymos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTests {
	@Test
	void verifiesModulithBoundaries() {
		ApplicationModules.of(GymOsApplication.class).verify();
	}
}
