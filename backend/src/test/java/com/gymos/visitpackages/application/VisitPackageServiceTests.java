package com.gymos.visitpackages.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackagePlanOperations;
import com.gymos.catalog.api.VisitPackagePlanOperations.VisitPackagePlanSnapshot;
import com.gymos.clients.api.operations.ClientLifecycleOperations;
import com.gymos.clients.api.operations.ClientLifecycleOperations.RegistrationFee;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.api.operations.VisitPackageSaleResult;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.PurchaseVisitPackageCommand;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitPackageServiceTests {
	@Test
	void paidPurchaseCreatesAndActivatesOneEntitlementWithCoverageSnapshots() {
		Fixture fixture = fixture("DEFAULT");
		when(fixture.lifecycle.registrationFee(any(), any(), any()))
				.thenReturn(new RegistrationFee(true, 15000, "Inscripción", "MXN", 6));
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID entitlementId = UUID.randomUUID();
		when(fixture.sales.createVisitPackagePurchase(any())).thenReturn(new VisitPackageSaleResult(
				saleId, "S-VISITAS", "PAID", 105000, 105000, 0, "MXN", true, paymentId));
		when(fixture.entitlements.createPending(
				any(), any(), any(), any(), eq(saleId), any(), anyInt(), anyInt(),
				any(), any(), any(), any())).thenReturn(entitlementId);
		when(fixture.entitlements.bySale(fixture.organizationId, saleId)).thenReturn(Optional.of(
				entitlement(fixture, entitlementId, saleId, "ACTIVE")));

		var result = fixture.service.purchase(fixture.principal, new PurchaseVisitPackageCommand(
				fixture.clientId, fixture.planId, "CASH", null, true, null, "purchase-1"), "correlation");

		assertThat(result.entitlementId()).isEqualTo(entitlementId);
		assertThat(result.entitlementStatus()).isEqualTo("ACTIVE");
		assertThat(result.totalCents()).isEqualTo(105000);
		verify(fixture.activation).activate(
				eq(fixture.organizationId), eq(fixture.branchId), eq(saleId), any(), eq("payment-completed:" + saleId));
	}

	@Test
	void waivedPackageDoesNotConsultOrChargeRegistration() {
		Fixture fixture = fixture("WAIVED");

		var quote = fixture.service.quote(fixture.principal, fixture.clientId, fixture.planId);

		assertThat(quote.registrationFeeCents()).isZero();
		assertThat(quote.totalCents()).isEqualTo(90000);
		verify(fixture.lifecycle, never()).registrationFee(any(), any(), any());
	}

	@Test
	void anotherOpenPackageIsRejectedBeforeCreatingASale() {
		Fixture fixture = fixture("DEFAULT");
		when(fixture.entitlements.open(fixture.organizationId, fixture.clientId))
				.thenReturn(Optional.of(entitlement(fixture, UUID.randomUUID(), UUID.randomUUID(), "ACTIVE")));

		assertThatThrownBy(() -> fixture.service.purchase(fixture.principal, new PurchaseVisitPackageCommand(
				fixture.clientId, fixture.planId, "CASH", null, true, null, "purchase-2"), "correlation"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("ya tiene un paquete");
		verify(fixture.sales, never()).createVisitPackagePurchase(any());
	}

	private Fixture fixture(String feePolicy) {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		VisitPackagePlanOperations plans = mock(VisitPackagePlanOperations.class);
		VisitPackageEntitlementPort entitlements = mock(VisitPackageEntitlementPort.class);
		ClientLifecycleOperations lifecycle = mock(ClientLifecycleOperations.class);
		SalesOperations sales = mock(SalesOperations.class);
		VisitPackageActivationService activation = mock(VisitPackageActivationService.class);
		AuditService audit = mock(AuditService.class);
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "owner@gym.test", "Dueño", Set.of("sale.create"));
		when(plans.activePlan(organizationId, branchId, planId)).thenReturn(Optional.of(
				new VisitPackagePlanSnapshot(
						planId, productId, "10-VISITAS", "10 visitas", 90000, "MXN", 10, 45,
						"ALL_BRANCHES", List.of(branchId), List.of(benefitId), true, feePolicy)));
		when(entitlements.lockActiveClient(organizationId, clientId)).thenReturn(true);
		when(entitlements.byIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
		when(entitlements.open(organizationId, clientId)).thenReturn(Optional.empty());
		when(entitlements.hasActiveLegacyPass(eq(organizationId), eq(clientId), any())).thenReturn(false);
		when(lifecycle.registrationFee(any(), any(), any())).thenReturn(RegistrationFee.none("MXN", 6));
		VisitPackageService service = new VisitPackageService(
				plans, entitlements, lifecycle, sales, activation, audit);
		return new Fixture(
				organizationId, branchId, clientId, planId, principal, entitlements, lifecycle,
				sales, activation, service);
	}

	private VisitPackageEntitlementView entitlement(
			Fixture fixture, UUID entitlementId, UUID saleId, String status
	) {
		return new VisitPackageEntitlementView(
				entitlementId, fixture.clientId, fixture.planId, saleId, "10 visitas", status,
				10, 10, null, null, null, List.of(fixture.branchId), List.of(),
				OffsetDateTime.now(), OffsetDateTime.now());
	}

	private record Fixture(
			UUID organizationId, UUID branchId, UUID clientId, UUID planId,
			GymOsPrincipal principal, VisitPackageEntitlementPort entitlements,
			ClientLifecycleOperations lifecycle, SalesOperations sales,
			VisitPackageActivationService activation, VisitPackageService service) {}
}
