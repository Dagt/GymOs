package com.gymos.access.application;

import com.gymos.common.api.BusinessException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral capabilities known by GymFort Cloud. Availability means that the
 * cloud-side contract can be configured; it never implies that a vendor adapter is installed.
 */
@Component
public class PhysicalAccessProviderCatalog {
	private final Map<String, ProviderDescriptor> providers;

	public PhysicalAccessProviderCatalog() {
		Map<String, ProviderDescriptor> configured = new LinkedHashMap<>();
		configured.put("HIKCENTRAL", new ProviderDescriptor(
				"HIKCENTRAL", "HikCentral Professional", "AVAILABLE", true,
				true, true, true, true, true,
				"Preparado en GymFort Cloud; requiere GymFort Local Connector y validación física."
		));
		configured.put("SIMULATED", new ProviderDescriptor(
				"SIMULATED", "Simulado (sin hardware)", "AVAILABLE", false,
				false, false, false, false, false,
				"Sólo diagnóstico interno; nunca abre un dispositivo físico."
		));
		configured.put("ZKTECO", planned("ZKTECO", "ZKTeco"));
		configured.put("SUPREMA", planned("SUPREMA", "Suprema"));
		configured.put("GENERIC_RELAY", planned("GENERIC_RELAY", "Relevador genérico"));
		configured.put("CLOUD_PROVIDER", planned("CLOUD_PROVIDER", "Proveedor cloud"));
		providers = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
	}

	public List<ProviderDescriptor> providers() {
		return providers.values().stream().toList();
	}

	public ProviderDescriptor requireAvailable(String providerKey) {
		String normalized = normalize(providerKey);
		ProviderDescriptor descriptor = providers.get(normalized);
		if (descriptor == null) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PROVIDER_UNKNOWN",
					"El proveedor de acceso físico no forma parte del catálogo de GymFort."
			);
		}
		if (!"AVAILABLE".equals(descriptor.availability())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PROVIDER_UNSUPPORTED",
					descriptor.displayName() + " está planificado, pero todavía no puede configurarse."
			);
		}
		return descriptor;
	}

	public ProviderDescriptor requireProductive(String providerKey) {
		ProviderDescriptor descriptor = requireAvailable(providerKey);
		if ("SIMULATED".equals(descriptor.providerKey())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PROVIDER_NOT_PRODUCTIVE",
					"El proveedor simulado no admite una conexión física productiva."
			);
		}
		return descriptor;
	}

	public boolean isProductive(String providerKey) {
		return providerKey != null && !"SIMULATED".equals(normalize(providerKey));
	}

	public String normalize(String providerKey) {
		return providerKey == null || providerKey.isBlank()
				? "HIKCENTRAL"
				: providerKey.trim().toUpperCase(Locale.ROOT);
	}

	private ProviderDescriptor planned(String key, String displayName) {
		return new ProviderDescriptor(
				key, displayName, "PLANNED", true,
				false, false, false, false, false,
				"Sin adapter ni contrato operativo; no seleccionable."
		);
	}

	public record ProviderDescriptor(
			String providerKey,
			String displayName,
			String availability,
			boolean requiresLocalConnector,
			boolean supportsPersonSynchronization,
			boolean supportsAccessLevels,
			boolean supportsResourceDiscovery,
			boolean supportsRemoteOpen,
			boolean supportsEvents,
			String implementationNote
	) {
	}
}
