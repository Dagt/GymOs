package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipOperationPolicyTests {
	private final MembershipRepository repository = mock(MembershipRepository.class);
	private final MembershipOperationPolicy policy = new MembershipOperationPolicy(repository);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID membershipId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(),
			organizationId,
			branchId,
			"reception@gymos.local",
			"Recepcion",
			Set.of("membership.renew")
	);

	@Test
	void expiredMembershipCanRenewAfterLazyExpirationReconciliation() {
		MembershipSummary expired = membership("EXPIRED");
		when(repository.byIdForUpdate(organizationId, branchId, membershipId))
				.thenReturn(Optional.of(expired));

		assertThat(policy.requireRenewableMembership(
				principal, membershipId, "MEMBERSHIP_NOT_RENEWABLE", "No renovable"))
				.isEqualTo(expired);
		verify(repository).expireMembershipIfDue(any(), any(), any());
	}

	@Test
	void expiredMembershipCannotUsePlanChangeOperation() {
		when(repository.byIdForUpdate(organizationId, branchId, membershipId))
				.thenReturn(Optional.of(membership("EXPIRED")));

		assertThatThrownBy(() -> policy.requireActiveMembership(
				principal, membershipId, "MEMBERSHIP_NOT_CHANGEABLE", "No modificable"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("MEMBERSHIP_NOT_CHANGEABLE"));
	}

	private MembershipSummary membership(String status) {
		LocalDate today = LocalDate.of(2026, 7, 29);
		return new MembershipSummary(
				membershipId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"Plan mensual",
				UUID.randomUUID(),
				"S-TEST",
				status,
				OffsetDateTime.now(),
				today.minusMonths(1),
				today.minusDays(4),
				today.minusDays(1)
		);
	}
}
