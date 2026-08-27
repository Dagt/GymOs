package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MembershipRenewalRegistrationFeeTests {
	@Test
	void returningClientRenewalPreservesRegistrationFeeBreakdown() {
		MembershipOperationPolicy policy = mock(MembershipOperationPolicy.class);
		SalesOperations sales = mock(SalesOperations.class);
		MembershipSaleResultResolver resultResolver = mock(MembershipSaleResultResolver.class);
		MembershipPricingService pricing = mock(MembershipPricingService.class);
		MembershipRenewalService service = new MembershipRenewalService(
				policy, new MembershipOperationIdempotency(), sales, resultResolver, pricing);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "manager@example.test", "Gerente",
				Set.of("membership.renew", "sale.create"));
		MembershipSummary membership = new MembershipSummary(
				membershipId, clientId, planId, "Plan mensual", UUID.randomUUID(), "V-OLD", "EXPIRED",
				OffsetDateTime.now().minusMonths(7), LocalDate.now().minusMonths(8),
				LocalDate.now().minusMonths(7), LocalDate.now().minusMonths(7));
		PlanForRenewal plan = new PlanForRenewal(
				planId, productId, "Plan mensual", 30, 3, false, 10_000, "MXN");
		MembershipQuoteResult quote = new MembershipQuoteResult(
				clientId, planId, "Plan mensual", 10_000, 1_500, "Inscripción",
				11_500, 1_000, 10_500, "MXN", List.of());
		when(policy.requireRenewableMembership(eq(principal), eq(membershipId), anyString(), anyString()))
				.thenReturn(membership);
		when(policy.requirePlanForRenewal(eq(organizationId), eq(planId), anyString())).thenReturn(plan);
		when(pricing.quote(organizationId, branchId, clientId, plan)).thenReturn(quote);
		when(sales.createMembershipRenewal(org.mockito.ArgumentMatchers.any()))
				.thenReturn(new com.gymos.sales.api.operations.MembershipSaleResult(
						UUID.randomUUID(), "V-NEW", "PAID", 10_500, 10_500, 0, "MXN", true));

		service.renew(
				principal, membershipId,
				new RenewMembershipCommand("CASH", null, true, null, "returning-client-renewal"),
				"test-correlation");

		ArgumentCaptor<CreateMembershipRenewalSaleCommand> command =
				ArgumentCaptor.forClass(CreateMembershipRenewalSaleCommand.class);
		verify(sales).createMembershipRenewal(command.capture());
		assertThat(command.getValue().planPriceCents()).isEqualTo(10_000);
		assertThat(command.getValue().registrationFeeCents()).isEqualTo(1_500);
		assertThat(command.getValue().registrationFeeLabel()).isEqualTo("Inscripción");
		assertThat(command.getValue().listPriceCents()).isEqualTo(11_500);
		assertThat(command.getValue().totalCents()).isEqualTo(10_500);
	}
}
