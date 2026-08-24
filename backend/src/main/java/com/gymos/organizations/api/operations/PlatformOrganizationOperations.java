package com.gymos.organizations.api.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

public interface PlatformOrganizationOperations {
	List<ManagedOrganization> organizations();

	Optional<ManagedOrganization> organization(UUID organizationId);

	ProvisionedOrganization create(CreateManagedOrganization command);

	record CreateManagedOrganization(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			String legalName,
			String tradeName,
			String taxId,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor,
			String branchName,
			String branchCode,
			String branchPhone,
			OffsetDateTime now
	) {
	}

	record ProvisionedOrganization(UUID organizationId, UUID branchId) {
	}

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
