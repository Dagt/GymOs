package com.gymos.organizations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.command.UpdateBranchCommand;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationTenantIsolationTests {
	@Test
	void tenantCannotUpdateBranchOutsideAuthenticatedOrganization() {
		OrganizationCommandRepository commandRepository = mock(OrganizationCommandRepository.class);
		OrganizationQueryRepository repository = mock(OrganizationQueryRepository.class);
		AuditService auditService = mock(AuditService.class);
		OrganizationService service = new OrganizationService(commandRepository, repository, auditService);

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("branch.update")
		);
		when(repository.userHasBranchAccess(userId, organizationId, foreignBranchId)).thenReturn(false);

		assertThatThrownBy(() -> service.updateBranch(
				principal,
				foreignBranchId,
				new UpdateBranchCommand("Sucursal ajena", "FOREIGN", "America/Monterrey", "+528112345678"),
				"tenant-isolation"
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("BRANCH_ACCESS_DENIED"));

		verify(repository).userHasBranchAccess(userId, organizationId, foreignBranchId);
		verifyNoInteractions(commandRepository, auditService);
	}

	@Test
	void tenantCannotListBranchesForArbitraryOrganization() {
		OrganizationCommandRepository commandRepository = mock(OrganizationCommandRepository.class);
		OrganizationQueryRepository repository = mock(OrganizationQueryRepository.class);
		AuditService auditService = mock(AuditService.class);
		OrganizationService service = new OrganizationService(commandRepository, repository, auditService);
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"manager@gymos.local",
				"Manager GymOS",
				Set.of("branch.read")
		);

		assertThatThrownBy(() -> service.branchesForOrganization(principal, UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PLATFORM_ACCESS_REQUIRED"));

		verifyNoInteractions(repository, commandRepository, auditService);
	}
}
