package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CreateVisitPackagePlanCommand;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.VisitPackagePlanPort;
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
		GymOsPrincipal principal = principal(organizationId, branchA);
		when(products.findBySku(organizationId, "10-VISITAS")).thenReturn(Optional.empty());
		when(packages.activeBranchIds(organizationId)).thenReturn(List.of(branchA, branchB));
		when(packages.branchesExist(organizationId, List.of(branchA, branchB))).thenReturn(true);
		when(packages.accessBenefitsExist(organizationId, List.of(benefitId))).thenReturn(true);
		when(packages.find(any(), any())).thenAnswer(invocation -> Optional.of(new VisitPackagePlanView(
				invocation.getArgument(1), UUID.randomUUID(), "10-VISITAS", "10 visitas", null,
				90000, "MXN", 10, 45, "ALL_BRANCHES", List.of(), List.of(benefitId),
				true, "WAIVED", "ACTIVE", 0, OffsetDateTime.now(), OffsetDateTime.now())));

		VisitPackagePlanView result = service.create(principal, new CreateVisitPackagePlanCommand(
				"10-visitas", "10 visitas", null, 90000, "mxn", 10, 45, "ALL_BRANCHES",
				List.of(), List.of(benefitId), true, "WAIVED", "ACTIVE"), "correlation");

		assertThat(result.sku()).isEqualTo("10-VISITAS");
		ArgumentCaptor<ProductMutation> mutation = ArgumentCaptor.forClass(ProductMutation.class);
		verify(productCommands).create(mutation.capture());
		assertThat(mutation.getValue().productType()).isEqualTo("SERVICE");
		assertThat(mutation.getValue().commercialSubtype()).isEqualTo("VISIT_PASS");
		verify(productCommands).attachBranch(organizationId, branchA, mutation.getValue().productId(), principal.userId(), mutation.getValue().now());
		verify(productCommands).attachBranch(organizationId, branchB, mutation.getValue().productId(), principal.userId(), mutation.getValue().now());
		verify(packages).replaceBranches(organizationId, result.id(), List.of(), principal.userId());
		verify(packages).replaceBenefits(organizationId, result.id(), List.of(benefitId), principal.userId());
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
						"SELECTED_BRANCHES", List.of(branchId), List.of(benefitId), true, "DEFAULT", "ACTIVE"),
				"correlation"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("acceso activo");
		verify(productCommands, never()).create(any());
	}

	private GymOsPrincipal principal(UUID organizationId, UUID branchId) {
		return new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "owner@gym.test", "Dueño",
				Set.of("catalog.create", "catalog.update"));
	}
}
