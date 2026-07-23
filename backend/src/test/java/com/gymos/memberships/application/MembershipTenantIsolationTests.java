package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.sales.api.operations.SalesOperations;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipTenantIsolationTests {
	@Test
	void membershipFromAnotherOrganizationOrBranchIsNotVisible() {
		MembershipRepository membershipRepository = mock(MembershipRepository.class);
		SalesOperations salesOperations = mock(SalesOperations.class);
		AuditService auditService = mock(AuditService.class);
		MembershipService service = new MembershipService(
				new MembershipQueryService(membershipRepository),
				new MembershipRenewalService(membershipRepository, salesOperations),
				new MembershipPlanChangeService(membershipRepository, salesOperations)
		);

		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				organizationId,
				branchId,
				"manager@gymos.local",
				"Gerente",
				Set.of("membership.read")
		);
		when(membershipRepository.byId(organizationId, branchId, membershipId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(principal, membershipId))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("MEMBERSHIP_NOT_FOUND"));

		verify(membershipRepository).byId(organizationId, branchId, membershipId);
		verifyNoInteractions(salesOperations, auditService);
	}
}
