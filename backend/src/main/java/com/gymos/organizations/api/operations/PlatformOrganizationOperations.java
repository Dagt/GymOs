package com.gymos.organizations.api.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformOrganizationOperations {
	List<ManagedOrganization> organizations();

	Optional<ManagedOrganization> organization(UUID organizationId);

	record ManagedOrganization(
			UUID id,
			String legalName,
			String tradeName,
			String taxId,
			String status,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			int branchCount,
			int activeBranchCount
	) {
	}
}
