package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackageBenefitUsage;
import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.catalog.application.command.CreateVisitPackagePlanCommand;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.VisitPackagePlanPort;
import com.gymos.catalog.application.port.VisitPackagePlanPort.BenefitCoverage;
import com.gymos.catalog.application.query.VisitPackagePlanView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VisitPackagePlanServiceTests {
	@Test
	void createsOneSellableDefinitionWithAllBranchAndAccessSnapshotsConfigured() {
		CatalogProductQueryPort products = mock(CatalogProductQueryPort.class);
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		AuditService audit = mock(AuditService.class);
		VisitPackagePlanService service = new VisitPackagePlanService(products, productCommands, packages, audit);
		UUID organizationId = UUID.randomUUID();
		UUID branchA = UUID.randomUUID();
		UUID branchB = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		VisitPackageBenefitUsage benefit = consumes(benefitId);
		GymOsPrincipal principal = principal(organizationId, branchA);
		when(products.findBySku(organizationId, "10-VISITAS")).thenReturn(Optional.empty());
		when(packages.activeBranchIds(organizationId)).thenReturn(List.of(branchA, branchB));
		when(packages.branchesExist(organizationId, List.of(branchA, branchB))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(benefitId))).thenReturn(true);
		when(packages.benefitCoverages(organizationId, List.of(benefit), List.of(branchA, branchB)))
				.thenReturn(List.of(new BenefitCoverage(
						benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(branchA, branchB))));
		when(packages.find(any(), any())).thenAnswer(invocation -> Optional.of(new VisitPackagePlanView(
				invocation.getArgument(1), UUID.randomUUID(), "10-VISITAS", "10 visitas", null,
				90000, "MXN", 10, 45, "ALL_BRANCHES", List.of(), List.of(benefit),
				true, "WAIVED", "ACTIVE", 0, OffsetDateTime.now(), OffsetDateTime.now())));

		VisitPackagePlanView result = service.create(principal, new CreateVisitPackagePlanCommand(
				"10-visitas", "10 visitas", null, 90000, "mxn", 10, 45, "ALL_BRANCHES",
				List.of(), List.of(benefit), true, "WAIVED", "ACTIVE"), "correlation");

		assertThat(result.sku()).isEqualTo("10-VISITAS");
		ArgumentCaptor<ProductMutation> mutation = ArgumentCaptor.forClass(ProductMutation.class);
		verify(productCommands).create(mutation.capture());
		assertThat(mutation.getValue().productType()).isEqualTo("SERVICE");
		assertThat(mutation.getValue().commercialSubtype()).isEqualTo("VISIT_PASS");
		verify(productCommands).attachBranch(organizationId, branchA, mutation.getValue().productId(), principal.userId(), mutation.getValue().now());
		verify(productCommands).attachBranch(organizationId, branchB, mutation.getValue().productId(), principal.userId(), mutation.getValue().now());
		verify(packages).replaceBranches(organizationId, result.id(), List.of(), principal.userId());
		verify(packages).replaceBenefits(organizationId, result.id(), List.of(benefit), principal.userId());
	}

	@Test
	void rejectsADefinitionWithoutAnActiveAccessBenefitBeforeCreatingAProduct() {
		CatalogProductQueryPort products = mock(CatalogProductQueryPort.class);
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				products, productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		when(packages.branchesExist(organizationId, List.of(branchId))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(benefitId))).thenReturn(false);

		assertThatThrownBy(() -> service.create(principal(organizationId, branchId),
				new CreateVisitPackagePlanCommand(
						"5-VISITAS", "5 visitas", null, 50000, "MXN", 5, 30,
						"SELECTED_BRANCHES", List.of(branchId), List.of(consumes(benefitId)), true, "DEFAULT", "ACTIVE"),
				"correlation"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("acceso activo");
		verify(productCommands, never()).create(any());
	}

	@Test
	void rejectsCreatingAPackageOnlyForTheOppositeBranch() {
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				mock(CatalogProductQueryPort.class), productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID activeBranch = UUID.randomUUID();
		UUID oppositeBranch = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		when(packages.branchesExist(organizationId, List.of(oppositeBranch))).thenReturn(true);

		assertThatThrownBy(() -> service.create(principal(organizationId, activeBranch),
				new CreateVisitPackagePlanCommand(
						"5-VISITAS", "5 visitas", null, 50000, "MXN", 5, 30,
						"SELECTED_BRANCHES", List.of(oppositeBranch), List.of(consumes(benefitId)),
						true, "DEFAULT", "ACTIVE"), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_ACTIVE_BRANCH_REQUIRED"));
		verify(productCommands, never()).create(any());
	}

	@Test
	void rejectsAnAccessThatHasNoEffectiveBranchInsideThePackage() {
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				mock(CatalogProductQueryPort.class), productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID activeBranch = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		when(packages.branchesExist(organizationId, List.of(activeBranch))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(benefitId))).thenReturn(true);
		VisitPackageBenefitUsage benefit = consumes(benefitId);
		when(packages.benefitCoverages(organizationId, List.of(benefit), List.of(activeBranch)))
				.thenReturn(List.of(new BenefitCoverage(
						benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of())));

		assertThatThrownBy(() -> service.create(principal(organizationId, activeBranch),
				new CreateVisitPackagePlanCommand(
						"5-VISITAS", "5 visitas", null, 50000, "MXN", 5, 30,
						"SELECTED_BRANCHES", List.of(activeBranch), List.of(benefit),
						true, "DEFAULT", "ACTIVE"), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_BENEFIT_OUTSIDE_SCOPE"));
		verify(productCommands, never()).create(any());
	}

	@Test
	void rejectsBranchesThatHaveNoneOfTheSelectedAccesses() {
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				mock(CatalogProductQueryPort.class), productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID activeBranch = UUID.randomUUID();
		UUID secondBranch = UUID.randomUUID();
		UUID localAccess = UUID.randomUUID();
		when(packages.branchesExist(organizationId, List.of(activeBranch, secondBranch))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(localAccess))).thenReturn(true);
		VisitPackageBenefitUsage benefit = consumes(localAccess);
		when(packages.benefitCoverages(
				organizationId, List.of(benefit), List.of(activeBranch, secondBranch)))
				.thenReturn(List.of(new BenefitCoverage(
						localAccess, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(activeBranch))));

		assertThatThrownBy(() -> service.create(principal(organizationId, activeBranch),
				new CreateVisitPackagePlanCommand(
						"5-VISITAS", "5 visitas", null, 50000, "MXN", 5, 30,
						"SELECTED_BRANCHES", List.of(activeBranch, secondBranch), List.of(benefit),
						true, "DEFAULT", "ACTIVE"), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_BRANCH_WITHOUT_ACCESS"));
		verify(productCommands, never()).create(any());
	}

	@Test
	void acceptsIncludedAccessWhenEveryBranchStillHasAConsumingAccess() {
		CatalogProductQueryPort products = mock(CatalogProductQueryPort.class);
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				products, productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID generalId = UUID.randomUUID();
		UUID saunaId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		VisitPackageBenefitUsage general = consumes(generalId);
		VisitPackageBenefitUsage sauna = included(saunaId);
		when(products.findBySku(organizationId, "10-VISITAS-SAUNA")).thenReturn(Optional.empty());
		when(packages.branchesExist(organizationId, List.of(branchId))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(generalId, saunaId))).thenReturn(true);
		when(packages.benefitCoverages(organizationId, List.of(general, sauna), List.of(branchId)))
				.thenReturn(List.of(
						new BenefitCoverage(generalId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(branchId)),
						new BenefitCoverage(saunaId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(branchId))));
		when(packages.find(any(), any())).thenAnswer(invocation -> Optional.of(new VisitPackagePlanView(
				invocation.getArgument(1), UUID.randomUUID(), "10-VISITAS-SAUNA", "10 visitas + Sauna", null,
				90000, "MXN", 10, 30, "SELECTED_BRANCHES", List.of(branchId), List.of(general, sauna),
				true, "DEFAULT", "ACTIVE", 0, OffsetDateTime.now(), OffsetDateTime.now())));

		VisitPackagePlanView created = service.create(
				principal,
				new CreateVisitPackagePlanCommand(
						"10-VISITAS-SAUNA", "10 visitas + Sauna", null, 90000, "MXN", 10, 30,
						"SELECTED_BRANCHES", List.of(branchId), List.of(general, sauna),
						true, "DEFAULT", "ACTIVE"),
				"correlation");

		verify(packages).replaceBenefits(
				organizationId, created.id(), List.of(general, sauna), principal.userId());
	}

	@Test
	void acceptsPackageCreatedInTecnologicaWithPremiumAccessRestrictedToCentro() {
		CatalogProductQueryPort products = mock(CatalogProductQueryPort.class);
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				products, productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID centro = UUID.randomUUID();
		UUID tecnologica = UUID.randomUUID();
		UUID generalCentroId = UUID.randomUUID();
		UUID generalTecnologicaId = UUID.randomUUID();
		UUID saunaId = UUID.randomUUID();
		UUID cryotherapyId = UUID.randomUUID();
		UUID premiumLockersId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, tecnologica);
		List<VisitPackageBenefitUsage> benefits = List.of(
				consumes(generalCentroId), consumes(generalTecnologicaId),
				included(saunaId), included(cryotherapyId), included(premiumLockersId));
		List<UUID> branches = List.of(tecnologica, centro);
		when(products.findBySku(organizationId, "10-VISITAS-PREMIUM")).thenReturn(Optional.empty());
		when(packages.branchesExist(organizationId, branches)).thenReturn(true);
		when(packages.accessBenefitsExist(
				organizationId, List.of(
						generalCentroId, generalTecnologicaId, saunaId, cryotherapyId, premiumLockersId)))
				.thenReturn(true);
		when(packages.benefitCoverages(organizationId, benefits, branches)).thenReturn(List.of(
				new BenefitCoverage(
						generalCentroId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(centro)),
				new BenefitCoverage(
						generalTecnologicaId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(tecnologica)),
				new BenefitCoverage(saunaId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(centro)),
				new BenefitCoverage(cryotherapyId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(centro)),
				new BenefitCoverage(premiumLockersId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(centro))));
		when(packages.find(any(), any())).thenAnswer(invocation -> Optional.of(new VisitPackagePlanView(
				invocation.getArgument(1), UUID.randomUUID(), "10-VISITAS-PREMIUM",
				"10 visitas multisucursal + premium Centro", null, 120000, "MXN", 10, 30,
				"SELECTED_BRANCHES", branches, benefits, true, "DEFAULT", "ACTIVE", 0,
				OffsetDateTime.now(), OffsetDateTime.now())));

		VisitPackagePlanView created = service.create(
				principal,
				new CreateVisitPackagePlanCommand(
						"10-VISITAS-PREMIUM", "10 visitas multisucursal + premium Centro", null,
						120000, "MXN", 10, 30, "SELECTED_BRANCHES", branches, benefits,
						true, "DEFAULT", "ACTIVE"),
				"correlation");

		verify(productCommands).attachBranch(
				eq(organizationId), eq(tecnologica), any(UUID.class), eq(principal.userId()),
				any(OffsetDateTime.class));
		verify(productCommands).attachBranch(
				eq(organizationId), eq(centro), any(UUID.class), eq(principal.userId()),
				any(OffsetDateTime.class));
		verify(packages).replaceBenefits(organizationId, created.id(), benefits, principal.userId());
	}

	@Test
	void rejectsTecnologicaOnlyPackageWhenItsPremiumAccessExistsOnlyInCentro() {
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				mock(CatalogProductQueryPort.class), productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID tecnologica = UUID.randomUUID();
		UUID generalTecnologicaId = UUID.randomUUID();
		UUID saunaCentroId = UUID.randomUUID();
		List<VisitPackageBenefitUsage> benefits = List.of(
				consumes(generalTecnologicaId), included(saunaCentroId));
		when(packages.branchesExist(organizationId, List.of(tecnologica))).thenReturn(true);
		when(packages.accessBenefitsExist(
				organizationId, List.of(generalTecnologicaId, saunaCentroId))).thenReturn(true);
		when(packages.benefitCoverages(organizationId, benefits, List.of(tecnologica))).thenReturn(List.of(
				new BenefitCoverage(
						generalTecnologicaId, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(tecnologica)),
				new BenefitCoverage(
						saunaCentroId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of())));

		assertThatThrownBy(() -> service.create(
				principal(organizationId, tecnologica),
				new CreateVisitPackagePlanCommand(
						"TEC-SAUNA", "Tecnológica con sauna", null, 80000, "MXN", 10, 30,
						"SELECTED_BRANCHES", List.of(tecnologica), benefits,
						true, "DEFAULT", "ACTIVE"),
				"correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_BENEFIT_OUTSIDE_SCOPE"));
		verify(productCommands, never()).create(any());
	}

	@Test
	void rejectsAPackageMadeOnlyOfIncludedAccesses() {
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		VisitPackagePlanPort packages = mock(VisitPackagePlanPort.class);
		VisitPackagePlanService service = new VisitPackagePlanService(
				mock(CatalogProductQueryPort.class), productCommands, packages, mock(AuditService.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saunaId = UUID.randomUUID();
		when(packages.branchesExist(organizationId, List.of(branchId))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(saunaId))).thenReturn(true);

		assertThatThrownBy(() -> service.create(
				principal(organizationId, branchId),
				new CreateVisitPackagePlanCommand(
						"SAUNA-INCLUIDO", "Sauna incluido", null, 50000, "MXN", 5, 30,
						"SELECTED_BRANCHES", List.of(branchId), List.of(included(saunaId)),
						true, "DEFAULT", "ACTIVE"),
				"correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_CONSUMING_BENEFIT_REQUIRED"));
		verify(productCommands, never()).create(any());
	}

	private VisitPackageBenefitUsage consumes(UUID benefitId) {
		return new VisitPackageBenefitUsage(benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT);
	}

	private VisitPackageBenefitUsage included(UUID benefitId) {
		return new VisitPackageBenefitUsage(benefitId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
	}

	private GymOsPrincipal principal(UUID organizationId, UUID branchId) {
		return new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "owner@gym.test", "Dueño",
				Set.of("catalog.create", "catalog.update"));
	}
}
