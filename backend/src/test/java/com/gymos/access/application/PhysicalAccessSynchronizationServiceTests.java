package com.gymos.access.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalBenefitMappingRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalBenefitAuthorizationModeRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ClientLinkRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DesiredStateRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.BenefitType;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalAccessSynchronizationServiceTests {
	private final AccessRepository accessRepository = mock(AccessRepository.class);
	private final PhysicalAccessRepository physicalRepository = mock(PhysicalAccessRepository.class);
	private final EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
	private final PhysicalAccessSynchronizationService service = new PhysicalAccessSynchronizationService(
			accessRepository, physicalRepository, eligibilityPolicy, new ObjectMapper(), mock(AuditService.class));
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID connectionId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final ConnectionRecord connection = new ConnectionRecord(
			connectionId, organizationId, branchId, "HIKCENTRAL", "HikCentral", "connector-main",
			true, "HEALTHY", null, null, null, null, null, null, null);

	@BeforeEach
	void clientAndLinkExist() {
		when(accessRepository.clientById(organizationId, clientId)).thenReturn(Optional.of(
				new ClientAccessRecord(clientId, "Andrea Socia", null, null, "ACTIVE")));
		when(physicalRepository.clientLink(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(new ClientLinkRecord(clientId, "person-42", OffsetDateTime.now())));
	}

	@Test
	void activeMappedBenefitProducesDurableUpsertJob() {
		UUID benefitId = UUID.randomUUID();
		when(eligibilityPolicy.evaluate(any())).thenReturn(new EligibilityDecision(
				true, Reason.ELIGIBLE, null, List.of(benefit(benefitId))));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of(new PhysicalBenefitMappingRecord(
						benefitId, "ACCESS-GYM", "Acceso gimnasio", "LEVEL-PRIMARY", "Principal")));
		DesiredStateRecord state = state("[\"LEVEL-PRIMARY\"]", "UPSERT", "PENDING", null);
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[\"LEVEL-PRIMARY\"]"), eq("UPSERT"), eq("PENDING"),
				isNull(), isNull(), eq("membership:activated:1"), isNull(), any())).thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(
				connection, clientId, "membership:activated:1", null, "MEMBERSHIP_EVENT", null);

		verify(physicalRepository).enqueueJob(
				eq(connection), eq(state), eq("person-42"), any(), eq("MEMBERSHIP_EVENT"), any());
	}

	@Test
	void inactiveEligibilityProducesRevokeJob() {
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(false, Reason.CLIENT_NOT_ACTIVE, null, List.of()));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of());
		DesiredStateRecord state = state("[]", "REVOKE", "PENDING", null);
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[]"), eq("REVOKE"), eq("PENDING"),
				isNull(), isNull(), eq("client:suspended:1"), isNull(), any())).thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(connection, clientId, "client:suspended:1", null, "CLIENT_EVENT", null);

		verify(physicalRepository).enqueueJob(
				eq(connection), eq(state), eq("person-42"), any(), eq("CLIENT_EVENT"), any());
	}

	@Test
	void planChangeUsesTheCurrentExactDesiredLevelSet() {
		UUID gymBenefitId = UUID.randomUUID();
		UUID removedSaunaBenefitId = UUID.randomUUID();
		when(eligibilityPolicy.evaluate(any())).thenReturn(new EligibilityDecision(
				true, Reason.ELIGIBLE, null, List.of(benefit(gymBenefitId))));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of(
						new PhysicalBenefitMappingRecord(
								gymBenefitId, "ACCESS-GYM", "Acceso gimnasio", "LEVEL-GYM", "General"),
						new PhysicalBenefitMappingRecord(
								removedSaunaBenefitId, "ACCESS-SAUNA", "Sauna", "LEVEL-SAUNA", "Sauna")));
		DesiredStateRecord state = state("[\"LEVEL-GYM\"]", "UPSERT", "PENDING", null);
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[\"LEVEL-GYM\"]"), eq("UPSERT"), eq("PENDING"),
				isNull(), isNull(), eq("membership:plan-changed:1"), isNull(), any())).thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(
				connection, clientId, "membership:plan-changed:1", null, "MEMBERSHIP_EVENT", null);

		verify(physicalRepository).upsertDesiredState(
				eq(connection), eq(clientId), eq("[\"LEVEL-GYM\"]"), eq("UPSERT"), eq("PENDING"),
				isNull(), isNull(), eq("membership:plan-changed:1"), isNull(), any());
		verify(physicalRepository).enqueueJob(
				eq(connection), eq(state), eq("person-42"), any(), eq("MEMBERSHIP_EVENT"), any());
	}

	@Test
	void missingExternalAccessLevelBlocksDeliveryWithoutLosingDesiredState() {
		UUID benefitId = UUID.randomUUID();
		when(eligibilityPolicy.evaluate(any())).thenReturn(new EligibilityDecision(
				true, Reason.ELIGIBLE, null, List.of(benefit(benefitId))));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of());
		DesiredStateRecord state = state("[]", "REVOKE", "BLOCKED", "ACCESS_LEVEL_MAPPING_REQUIRED");
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[]"), eq("REVOKE"), eq("BLOCKED"),
				eq("ACCESS_LEVEL_MAPPING_REQUIRED"), any(), eq("benefit:changed:1"), isNull(), any()))
				.thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(connection, clientId, "benefit:changed:1", null, "BENEFIT_CONFIGURATION", null);

		verify(physicalRepository, never()).enqueueJob(any(), any(), any(), any(), any(), any());
	}

	@Test
	void onlineBenefitIsNotGrantedAsAnAutonomousProviderAccessLevel() {
		UUID benefitId = UUID.randomUUID();
		when(eligibilityPolicy.evaluate(any())).thenReturn(new EligibilityDecision(
				true, Reason.ELIGIBLE, null, List.of(benefit(benefitId))));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of(new PhysicalBenefitMappingRecord(
						benefitId, "ACCESS-GYM", "Acceso gimnasio", "LEVEL-PRIMARY", "Principal")));
		when(accessRepository.physicalBenefitAuthorizationModes(organizationId, branchId, connectionId))
				.thenReturn(List.of(new PhysicalBenefitAuthorizationModeRecord(benefitId, "GYMFORT_ONLINE")));
		DesiredStateRecord state = state("[]", "REVOKE", "PENDING", null);
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[]"), eq("REVOKE"), eq("PENDING"),
				isNull(), isNull(), eq("online-device:1"), isNull(), any())).thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(connection, clientId, "online-device:1", null, "DEVICE_CONFIGURATION", null);

		verify(physicalRepository).enqueueJob(
				eq(connection), eq(state), eq("person-42"), any(), eq("DEVICE_CONFIGURATION"), any());
	}

	@Test
	void mixedProviderAndOnlineModesForTheSameBenefitAreBlocked() {
		UUID benefitId = UUID.randomUUID();
		when(eligibilityPolicy.evaluate(any())).thenReturn(new EligibilityDecision(
				true, Reason.ELIGIBLE, null, List.of(benefit(benefitId))));
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of(new PhysicalBenefitMappingRecord(
						benefitId, "ACCESS-GYM", "Acceso gimnasio", "LEVEL-PRIMARY", "Principal")));
		when(accessRepository.physicalBenefitAuthorizationModes(organizationId, branchId, connectionId))
				.thenReturn(List.of(
						new PhysicalBenefitAuthorizationModeRecord(benefitId, "PROVIDER_MANAGED"),
						new PhysicalBenefitAuthorizationModeRecord(benefitId, "GYMFORT_ONLINE")));
		DesiredStateRecord state = state(
				"[\"LEVEL-PRIMARY\"]", "UPSERT", "BLOCKED", "PHYSICAL_AUTHORIZATION_MODE_CONFLICT");
		when(physicalRepository.upsertDesiredState(
				eq(connection), eq(clientId), eq("[\"LEVEL-PRIMARY\"]"), eq("UPSERT"), eq("BLOCKED"),
				eq("PHYSICAL_AUTHORIZATION_MODE_CONFLICT"), any(), eq("mixed-device:1"), isNull(), any()))
				.thenReturn(state);
		when(physicalRepository.desiredState(organizationId, branchId, connectionId, clientId))
				.thenReturn(Optional.of(state));

		service.reconcileClient(connection, clientId, "mixed-device:1", null, "DEVICE_CONFIGURATION", null);

		verify(physicalRepository, never()).enqueueJob(any(), any(), any(), any(), any(), any());
	}

	private EligibleBenefit benefit(UUID benefitId) {
		return new EligibleBenefit(
				benefitId, "ACCESS-GYM", "Acceso gimnasio", BenefitType.ACCESS, "NONE",
				null, null, false, 10, UUID.randomUUID(), UUID.randomUUID(), "Mensual");
	}

	private DesiredStateRecord state(String levels, String action, String status, String blockedReason) {
		return new DesiredStateRecord(
				UUID.randomUUID(), organizationId, branchId, connectionId, clientId, levels, action, status,
				OffsetDateTime.now(), null, null, null, 1, blockedReason, "source", null);
	}
}
