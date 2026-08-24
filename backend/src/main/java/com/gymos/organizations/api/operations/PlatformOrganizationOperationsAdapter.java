package com.gymos.organizations.api.operations;

import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.ManagedOrganizationSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PlatformOrganizationOperationsAdapter implements PlatformOrganizationOperations {
	private final OrganizationQueryRepository repository;

	PlatformOrganizationOperationsAdapter(OrganizationQueryRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<ManagedOrganization> organizations() {
		return repository.findAllOrganizations().stream().map(this::toOperation).toList();
	}

	@Override
	public Optional<ManagedOrganization> organization(UUID organizationId) {
		return repository.findManagedOrganization(organizationId).map(this::toOperation);
	}

	private ManagedOrganization toOperation(ManagedOrganizationSummary source) {
		return new ManagedOrganization(
				source.id(), source.legalName(), source.tradeName(), source.taxId(), source.status(),
				source.contactEmail(), source.contactPhone(), source.brandName(), source.logoUrl(),
				source.branchCount(), source.activeBranchCount());
	}
}
