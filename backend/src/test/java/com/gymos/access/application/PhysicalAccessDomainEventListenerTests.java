package com.gymos.access.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.benefits.api.events.AccessBenefitConfigurationChangedV1;
import com.gymos.clients.api.events.ClientAccessStatusChangedV1;
import com.gymos.memberships.api.events.MembershipActivatedV1;
import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalAccessDomainEventListenerTests {
	private final PhysicalAccessSynchronizationService synchronizationService =
			mock(PhysicalAccessSynchronizationService.class);
	private final PhysicalAccessRepository repository = mock(PhysicalAccessRepository.class);
	private final PhysicalAccessDomainEventListener listener =
			new PhysicalAccessDomainEventListener(synchronizationService, repository);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final OffsetDateTime now = OffsetDateTime.now();

	@Test
	void everyMembershipLifecycleEventRecalculatesTheClientsRights() {
		listener.onMembershipActivated(new MembershipActivatedV1(
				"activated", organizationId, branchId, clientId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusMonths(1), now));
		listener.onMembershipRenewed(new MembershipRenewedV1(
				"renewed", organizationId, branchId, clientId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusMonths(1), now));
		listener.onMembershipPlanChanged(new MembershipPlanChangedV1(
				"changed", organizationId, branchId, clientId, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusMonths(1), now));
		listener.onMembershipExpired(new MembershipExpiredV1(
				"expired", organizationId, branchId, clientId, UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.now(), LocalDate.now(), LocalDate.now(), now));

		verify(synchronizationService).reconcileClientAcrossConnections(
				organizationId, clientId, "activated", null, "MEMBERSHIP_EVENT");
		verify(synchronizationService).reconcileClientAcrossConnections(
				organizationId, clientId, "renewed", null, "MEMBERSHIP_EVENT");
		verify(synchronizationService).reconcileClientAcrossConnections(
				organizationId, clientId, "changed", null, "MEMBERSHIP_EVENT");
		verify(synchronizationService).reconcileClientAcrossConnections(
				organizationId, clientId, "expired", null, "MEMBERSHIP_EVENT");
	}

	@Test
	void clientStatusAndBenefitConfigurationEventsAlsoRecalculateRights() {
		listener.onClientStatusChanged(new ClientAccessStatusChangedV1(
				"suspended", organizationId, clientId, "ACTIVE", "SUSPENDED", now));
		ConnectionRecord connection = new ConnectionRecord(
				UUID.randomUUID(), organizationId, branchId, "HIKCENTRAL", "HikCentral", "connector",
				true, "HEALTHY", null, null, null, null, null, null, null);
		when(repository.connections(organizationId, branchId)).thenReturn(List.of(connection));
		listener.onAccessBenefitConfigurationChanged(new AccessBenefitConfigurationChangedV1(
				"benefit", organizationId, branchId, UUID.randomUUID(), List.of(UUID.randomUUID()), now));

		verify(synchronizationService).reconcileClientAcrossConnections(
				organizationId, clientId, "suspended", null, "CLIENT_EVENT");
		verify(synchronizationService).reconcileConnection(
				connection, "benefit", null, "BENEFIT_CONFIGURATION", null);
	}

	@Test
	void organizationWideAccessBenefitReconcilesConnectionsInEveryBranch() {
		ConnectionRecord firstConnection = new ConnectionRecord(
				UUID.randomUUID(), organizationId, branchId, "HIKCENTRAL", "HikCentral", "connector-one",
				true, "HEALTHY", null, null, null, null, null, null, null);
		ConnectionRecord secondConnection = new ConnectionRecord(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "HIKCENTRAL", "HikCentral", "connector-two",
				true, "HEALTHY", null, null, null, null, null, null, null);
		when(repository.connections(organizationId, null)).thenReturn(List.of(firstConnection, secondConnection));

		listener.onAccessBenefitConfigurationChanged(new AccessBenefitConfigurationChangedV1(
				"global-benefit", organizationId, null, UUID.randomUUID(), List.of(UUID.randomUUID()), now));

		verify(synchronizationService).reconcileConnection(
				firstConnection, "global-benefit", null, "BENEFIT_CONFIGURATION", null);
		verify(synchronizationService).reconcileConnection(
				secondConnection, "global-benefit", null, "BENEFIT_CONFIGURATION", null);
	}
}
