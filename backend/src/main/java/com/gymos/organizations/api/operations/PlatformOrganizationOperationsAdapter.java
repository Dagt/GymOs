package com.gymos.organizations.api.operations;

import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.CreateBranchRecord;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.CreateOrganizationRecord;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.ManagedOrganizationSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PlatformOrganizationOperationsAdapter implements PlatformOrganizationOperations {
	private final OrganizationQueryRepository repository;
	private final OrganizationCommandRepository commandRepository;

	PlatformOrganizationOperationsAdapter(
			OrganizationQueryRepository repository,
			OrganizationCommandRepository commandRepository
	) {
		this.repository = repository;
		this.commandRepository = commandRepository;
	}

	@Override
	public List<ManagedOrganization> organizations() {
		return repository.findAllOrganizations().stream().map(this::toOperation).toList();
	}

	@Override
	public Optional<ManagedOrganization> organization(UUID organizationId) {
		return repository.findManagedOrganization(organizationId).map(this::toOperation);
	}

	@Override
	public ProvisionedOrganization create(CreateManagedOrganization command) {
		commandRepository.createOrganization(new CreateOrganizationRecord(
				command.organizationId(), command.actorUserId(), command.legalName(), command.tradeName(),
				command.taxId(), command.timezone(), command.defaultCurrency(), command.contactEmail(),
				command.contactPhone(), command.brandName(), command.logoUrl(), command.primaryColor(),
				command.accentColor(), command.surfaceColor(), command.now()));
		commandRepository.createBranch(new CreateBranchRecord(
				command.branchId(), command.organizationId(), command.actorUserId(), command.branchName(),
				command.branchCode(), command.timezone(), command.branchPhone(), command.now()));
		return new ProvisionedOrganization(command.organizationId(), command.branchId());
	}

	private ManagedOrganization toOperation(ManagedOrganizationSummary source) {
		return new ManagedOrganization(
				source.id(), source.legalName(), source.tradeName(), source.taxId(), source.status(),
				source.contactEmail(), source.contactPhone(), source.brandName(), source.logoUrl(),
				source.branchCount(), source.activeBranchCount());
	}
}
