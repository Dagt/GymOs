package com.gymos.payments.api;

import java.util.UUID;

public interface PaymentConfigurationOperations {
	PaymentConfigurationView configuration(UUID organizationId, UUID branchId);

	void requireReceptionMethod(UUID organizationId, UUID branchId, String method);
}
