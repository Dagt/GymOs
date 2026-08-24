package com.gymos.access.application;

import com.gymos.benefits.api.events.AccessBenefitConfigurationChangedV1;
import com.gymos.clients.api.events.ClientAccessStatusChangedV1;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.memberships.api.events.MembershipActivatedV1;
import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.memberships.api.events.MembershipPlanChangedV1;
import com.gymos.memberships.api.events.MembershipRenewedV1;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class PhysicalAccessDomainEventListener {
	private final PhysicalAccessSynchronizationService synchronizationService;
	private final PhysicalAccessRepository connectionLookup;

	public PhysicalAccessDomainEventListener(
			PhysicalAccessSynchronizationService synchronizationService,
			PhysicalAccessRepository connectionLookup
	) {
		this.synchronizationService = synchronizationService;
		this.connectionLookup = connectionLookup;
	}

	@ApplicationModuleListener
	public void onMembershipActivated(MembershipActivatedV1 event) {
		synchronizationService.reconcileClientAcrossConnections(
				event.organizationId(), event.clientId(), event.eventKey(), null, "MEMBERSHIP_EVENT");
	}

	@ApplicationModuleListener
	public void onMembershipRenewed(MembershipRenewedV1 event) {
		synchronizationService.reconcileClientAcrossConnections(
				event.organizationId(), event.clientId(), event.eventKey(), null, "MEMBERSHIP_EVENT");
	}

	@ApplicationModuleListener
	public void onMembershipPlanChanged(MembershipPlanChangedV1 event) {
		synchronizationService.reconcileClientAcrossConnections(
				event.organizationId(), event.clientId(), event.eventKey(), null, "MEMBERSHIP_EVENT");
	}

	@ApplicationModuleListener
	public void onMembershipExpired(MembershipExpiredV1 event) {
		synchronizationService.reconcileClientAcrossConnections(
				event.organizationId(), event.clientId(), event.eventKey(), null, "MEMBERSHIP_EVENT");
	}

	@ApplicationModuleListener
	public void onClientStatusChanged(ClientAccessStatusChangedV1 event) {
		synchronizationService.reconcileClientAcrossConnections(
				event.organizationId(), event.clientId(), event.eventKey(), null, "CLIENT_EVENT");
	}

	@ApplicationModuleListener
	public void onAccessBenefitConfigurationChanged(AccessBenefitConfigurationChangedV1 event) {
		for (var connection : synchronizationServiceConnections(event)) {
			synchronizationService.reconcileConnection(
					connection, event.eventKey(), null, "BENEFIT_CONFIGURATION", null);
		}
	}

	private java.util.List<PhysicalAccessRepository.ConnectionRecord>
			synchronizationServiceConnections(AccessBenefitConfigurationChangedV1 event) {
		return connectionLookup.connections(event.organizationId(), event.branchId());
	}
}
