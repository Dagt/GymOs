package com.gymos.benefits.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.benefits.application.command.AssignClientBenefitCommand;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.benefits.infrastructure.BenefitRepository.BenefitSummary;
import com.gymos.benefits.infrastructure.BenefitRepository.ClientBenefitSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BenefitPlanLinkPolicyTests {
	private final BenefitRepository repository = mock(BenefitRepository.class);
	private final AuditService auditService = mock(AuditService.class);
	private final BenefitService service = new BenefitService(
			repository, auditService, mock(ApplicationEventPublisher.class));
	private final UUID organizationId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(), organizationId, UUID.randomUUID(), "owner@gymos.local", "Owner", Set.of("benefit.manage"));

	@Test
	void accessBenefitRequiresAtLeastOnePlan() {
		assertCode(command("ACCESS", List.of()), "BENEFIT_PLAN_REQUIRED");
		verifyNoInteractions(repository, auditService);
	}

	@Test
	void pricingBenefitCannotLinkPlans() {
		assertCode(command("AGREEMENT", List.of(UUID.randomUUID())), "BENEFIT_PLAN_LINK_NOT_ALLOWED");
		verifyNoInteractions(repository, auditService);
	}

	@Test
	void specialPriceRequiresAtLeastOneApplicablePlan() {
		CreateBenefitCommand command = new CreateBenefitCommand(
				null, "STUDENT-FULL", "Precio estudiante", "STUDENT", "SPECIAL_PRICE", 60_000, null,
				false, false, 10, null, null, "ACTIVE", List.of(), "LOCAL", List.of());

		assertCode(command, "BENEFIT_APPLICABLE_PLAN_REQUIRED");
		verifyNoInteractions(repository, auditService);
	}

	@Test
	void percentageBenefitCannotAccidentallyCarryApplicablePlans() {
		CreateBenefitCommand command = new CreateBenefitCommand(
				null, "STUDENT", "Estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, false, 10, null, null, "ACTIVE", List.of(), "LOCAL", List.of(UUID.randomUUID()));

		assertCode(command, "BENEFIT_APPLICABLE_PLAN_NOT_ALLOWED");
		verifyNoInteractions(repository, auditService);
	}

	@Test
	void accessBenefitRejectsPlansOutsideTheOrganization() {
		UUID planId = UUID.randomUUID();
		when(repository.membershipPlansExist(organizationId, List.of(planId))).thenReturn(false);

		assertCode(command("ACCESS", List.of(planId)), "MEMBERSHIP_PLAN_NOT_AVAILABLE");

		verify(repository).membershipPlansExist(organizationId, List.of(planId));
		verifyNoInteractions(auditService);
	}

	@Test
	void accessBenefitCannotCarryAPriceDiscount() {
		UUID planId = UUID.randomUUID();
		when(repository.membershipPlansExist(organizationId, List.of(planId))).thenReturn(true);
		CreateBenefitCommand invalid = new CreateBenefitCommand(
				null, "ACCESS-SAUNA", "Acceso sauna", "ACCESS", "PERCENT", null, 10.0,
				false, false, 10, null, null, "ACTIVE", List.of(planId));

		assertCode(invalid, "BENEFIT_ACCESS_DISCOUNT_NOT_ALLOWED");
	}

	@Test
	void accessBenefitCannotBeAssignedDirectlyToClient() {
		UUID clientId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		when(repository.clientExists(organizationId, clientId)).thenReturn(true);
		when(repository.branchExists(organizationId, principal.branchId())).thenReturn(true);
		when(repository.benefit(organizationId, benefitId)).thenReturn(java.util.Optional.of(
				new BenefitSummary(
						benefitId, null, "ACCESS-SAUNA", "Acceso sauna", "ACCESS", "NONE",
						null, null, false, false, 10, null, null, "ACTIVE", List.of(UUID.randomUUID())
				)
		));

		assertThatThrownBy(() -> service.assignToClient(
				principal,
				new AssignClientBenefitCommand(
						clientId, benefitId, null, LocalDate.now(), LocalDate.now().plusMonths(1), null),
				"test"
		)).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo("BENEFIT_ACCESS_INHERITED"));
	}

	@Test
	void pricingBenefitAssignmentRequiresExpiration() {
		UUID clientId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		when(repository.clientExists(organizationId, clientId)).thenReturn(true);
		when(repository.branchExists(organizationId, principal.branchId())).thenReturn(true);
		when(repository.benefit(organizationId, benefitId)).thenReturn(java.util.Optional.of(
				new BenefitSummary(
						benefitId, null, "STUDENT", "Estudiante", "STUDENT", "PERCENT",
						null, 10.0, false, false, 10, null, null, "ACTIVE", List.of()
				)
		));

		assertThatThrownBy(() -> service.assignToClient(
				principal,
				new AssignClientBenefitCommand(clientId, benefitId, null, LocalDate.now(), null, null),
				"test"
		)).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo("BENEFIT_ASSIGNMENT_EXPIRATION_REQUIRED"));
	}

	@Test
	void benefitAssignmentRejectsBranchOutsideTheOrganization() {
		UUID clientId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		assertThatThrownBy(() -> service.assignToClient(
				principal,
				new AssignClientBenefitCommand(
						clientId, benefitId, foreignBranchId, LocalDate.now(), LocalDate.now().plusMonths(1), null),
				"test"
		)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
	}

	@Test
	void benefitVerificationRejectsAnAssignmentFromAnotherBranch() {
		UUID clientBenefitId = UUID.randomUUID();
		when(repository.clientBenefit(organizationId, clientBenefitId)).thenReturn(java.util.Optional.of(
				new ClientBenefitSummary(
						clientBenefitId,
						UUID.randomUUID(),
						UUID.randomUUID(),
						UUID.randomUUID(),
						"Estudiante",
						"ASSIGNED",
						LocalDate.now(),
						LocalDate.now().plusMonths(1),
						null
				)
		));

		assertThatThrownBy(() -> service.verify(principal, clientBenefitId, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("CLIENT_BENEFIT_BRANCH_MISMATCH"));
	}

	@Test
	void benefitWithoutExplicitScopeDefaultsToTheSelectedBranch() {
		CreateBenefitCommand command = new CreateBenefitCommand(
				null, "STUDENT", "Estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, false, 10, null, null, "ACTIVE", List.of());
		UUID benefitId = UUID.randomUUID();
		when(repository.membershipPlansExist(organizationId, List.of())).thenReturn(true);
		when(repository.branchExists(organizationId, principal.branchId())).thenReturn(true);
		when(repository.codeExists(organizationId, principal.branchId(), "STUDENT")).thenReturn(false);
		when(repository.createBenefit(principal, principal.branchId(), command)).thenReturn(benefitId);
		when(repository.benefit(organizationId, benefitId)).thenReturn(java.util.Optional.of(
				new BenefitSummary(
						benefitId, principal.branchId(), "STUDENT", "Estudiante", "STUDENT", "PERCENT",
						null, 10.0, false, false, 10, null, null, "ACTIVE", List.of()
				)
		));

		BenefitSummary created = service.createBenefit(principal, command, "test");

		assertThat(created.branchId()).isEqualTo(principal.branchId());
		verify(repository).createBenefit(principal, principal.branchId(), command);
	}

	private void assertCode(CreateBenefitCommand command, String expectedCode) {
		assertThatThrownBy(() -> service.createBenefit(principal, command, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo(expectedCode));
	}

	private CreateBenefitCommand command(String type, List<UUID> planIds) {
		return new CreateBenefitCommand(
				null, "ACCESS-SAUNA", "Acceso sauna", type, "NONE", null, null,
				false, false, 10, null, null, "ACTIVE", planIds);
	}
}
