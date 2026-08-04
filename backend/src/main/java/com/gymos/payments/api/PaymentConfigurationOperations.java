package com.gymos.payments.api;

import java.util.UUID;

public interface PaymentConfigurationOperations {
	PaymentConfigurationView configuration(UUID organizationId);

	void requireReceptionMethod(UUID organizationId, String method);
}
