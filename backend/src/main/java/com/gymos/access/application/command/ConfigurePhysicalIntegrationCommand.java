package com.gymos.access.application.command;

import java.util.List;
import java.util.UUID;

public record ConfigurePhysicalIntegrationCommand(
		boolean enabled,
		String providerKey,
		String displayName,
		String connectorKey,
		List<BenefitMapping> benefitMappings
) {
	public ConfigurePhysicalIntegrationCommand {
		benefitMappings = benefitMappings == null ? List.of() : List.copyOf(benefitMappings);
	}

	public record BenefitMapping(
			UUID benefitId,
			String externalAccessLevelRef,
			String externalAccessLevelName
	) {
	}
}
