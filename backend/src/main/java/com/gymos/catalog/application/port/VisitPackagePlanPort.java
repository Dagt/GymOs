package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.VisitPackagePlanView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackagePlanPort {
	List<VisitPackagePlanView> list(UUID organizationId, UUID branchId);
	Optional<VisitPackagePlanView> find(UUID organizationId, UUID planId);
	void create(
			UUID organizationId, UUID id, UUID productId, int totalVisits, int validityDays,
			String branchScope, boolean selfServiceAcquisitionEnabled, String registrationFeePolicy,
			String status, UUID userId, OffsetDateTime now);
	boolean update(
			UUID organizationId, UUID id, int totalVisits, int validityDays, String branchScope,
			boolean selfServiceAcquisitionEnabled, String registrationFeePolicy, String status,
			long expectedVersion, UUID userId, OffsetDateTime now);
	void replaceBranches(UUID organizationId, UUID planId, List<UUID> branchIds, UUID userId);
	void replaceBenefits(UUID organizationId, UUID planId, List<UUID> benefitIds, UUID userId);
	boolean branchesExist(UUID organizationId, List<UUID> branchIds);
	List<UUID> activeBranchIds(UUID organizationId);
	boolean accessBenefitsExist(UUID organizationId, List<UUID> benefitIds);
	boolean configuredProduct(UUID organizationId, UUID productId);
}
