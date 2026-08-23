package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.common.api.BusinessException;
import org.junit.jupiter.api.Test;

class PhysicalAccessProviderCatalogTests {
	private final PhysicalAccessProviderCatalog catalog = new PhysicalAccessProviderCatalog();

	@Test
	void exposesCapabilitiesWithoutClaimingPlannedProvidersAreOperational() {
		var hikCentral = catalog.requireProductive("hikcentral");

		assertThat(hikCentral.providerKey()).isEqualTo("HIKCENTRAL");
		assertThat(hikCentral.requiresLocalConnector()).isTrue();
		assertThat(hikCentral.supportsPersonSynchronization()).isTrue();
		assertThat(hikCentral.implementationNote()).contains("requiere GymFort Local Connector");
		assertThat(catalog.providers())
				.filteredOn(provider -> provider.availability().equals("PLANNED"))
				.extracting(PhysicalAccessProviderCatalog.ProviderDescriptor::providerKey)
				.contains("ZKTECO", "SUPREMA", "GENERIC_RELAY", "CLOUD_PROVIDER");
	}

	@Test
	void rejectsPlannedAndSimulatedProvidersForProductiveConnections() {
		assertThatThrownBy(() -> catalog.requireProductive("ZKTECO"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_PROVIDER_UNSUPPORTED"));
		assertThatThrownBy(() -> catalog.requireProductive("SIMULATED"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_PROVIDER_NOT_PRODUCTIVE"));
	}
}
