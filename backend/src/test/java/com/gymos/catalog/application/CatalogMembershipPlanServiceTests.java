package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.port.CatalogPlanCommandPort;
import com.gymos.catalog.application.port.CatalogPlanQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogMembershipPlanServiceTests {
	@Test
	void existingOrganizationSkuAttachesThePlanToTheCurrentBranchWithoutDuplicatingIt() {
		CatalogProductQueryPort products = mock(CatalogProductQueryPort.class);
		CatalogPlanQueryPort plans = mock(CatalogPlanQueryPort.class);
		CatalogProductCommandPort productCommands = mock(CatalogProductCommandPort.class);
		CatalogPlanCommandPort planCommands = mock(CatalogPlanCommandPort.class);
		AuditService audit = mock(AuditService.class);
		CatalogMembershipPlanService service = new CatalogMembershipPlanService(
				products, plans, productCommands, planCommands,
				new CatalogBenefitsSerializer(new ObjectMapper()), audit
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "manager@gym.test", "Gerente", Set.of("catalog.manage")
		);
		ProductSummaryView product = mock(ProductSummaryView.class);
		when(product.id()).thenReturn(productId);
		when(product.productType()).thenReturn("MEMBERSHIP_PLAN");
		PlanView existing = new PlanView(
				planId, productId, "MENSUAL", "Mensual", 75000, "MXN", 30, 3,
				"{}", false, "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now()
		);
		when(products.findBySku(organizationId, "MENSUAL")).thenReturn(Optional.of(product));
		when(plans.findPlanByProduct(organizationId, productId)).thenReturn(Optional.of(existing));
		when(plans.findPlan(organizationId, branchId, planId)).thenReturn(Optional.of(existing));

		PlanView result = service.createMembershipPlan(
				principal,
				new CreateMembershipPlanCommand(
						"mensual", "Mensual", null, 75000, "MXN", 30, 3, null, false, "ACTIVE"
				),
				"correlation"
		);

		assertThat(result).isEqualTo(existing);
		verify(productCommands).attachBranch(
				eq(organizationId), eq(branchId), eq(productId), eq(userId), any(OffsetDateTime.class)
		);
		verify(productCommands, never()).create(any());
		verify(planCommands, never()).create(any());
	}
}
