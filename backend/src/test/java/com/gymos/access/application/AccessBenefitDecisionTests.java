package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.application.command.AccessRequestCommand;
import com.gymos.access.application.command.ManualOpenCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessBenefitDecisionTests {
	@Test
	void activeMembershipHasPriorityAndDoesNotConsumeAVisitPackage() {
		AccessRepository repository = mock(AccessRepository.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		VisitPackageAccessOperations visitPackages = mock(VisitPackageAccessOperations.class);
		AccessService service = service(repository, mock(AuditService.class), eligibilityPolicy, visitPackages);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID logId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "reception@gymos.local", "Recepcion", Set.of());
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Entrada", "MAIN", "TURNSTILE", "ACTIVE", "SIMULATED", null);
		ClientAccessRecord client = new ClientAccessRecord(clientId, "Socio", null, null, "ACTIVE");
		ActiveMembership membership = new ActiveMembership(
				membershipId, UUID.randomUUID(), "Mensual", LocalDate.now(), LocalDate.now().plusDays(30),
				LocalDate.now().plusDays(33));
		AccessDecisionResponse granted = new AccessDecisionResponse(
				logId, "GRANTED", "MEMBERSHIP_CHECK", "Vigente", "SIMULATED_OPEN", OffsetDateTime.now(),
				device.name(), device.deviceCode(), clientId, client.fullName(), membershipId,
				membership.planName(), null);
		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(client));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId)).thenReturn(List.of());
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(true, Reason.ELIGIBLE, membership, List.of()));
		when(repository.createAccessLog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any())).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(granted));

		assertThat(service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null, "membership-first"),
				correlationId.toString()).accessType()).isEqualTo("MEMBERSHIP_CHECK");

		verify(visitPackages, never()).authorizeForAccess(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void consumesCompatibleVisitPackageWhenMembershipDoesNotGrantAccess() {
		AccessRepository repository = mock(AccessRepository.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		VisitPackageAccessOperations visitPackages = mock(VisitPackageAccessOperations.class);
		AccessService service = service(repository, mock(AuditService.class), eligibilityPolicy, visitPackages);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		UUID consumptionId = UUID.randomUUID();
		UUID logId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "reception@gymos.local", "Recepcion", Set.of());
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Entrada", "MAIN", "TURNSTILE", "ACTIVE", "SIMULATED", null);
		ClientAccessRecord client = new ClientAccessRecord(clientId, "Socio", null, null, "ACTIVE");
		VisitPackageAccessResult consumption = new VisitPackageAccessResult(
				consumptionId, consumptionId, benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
				UUID.randomUUID(), clientId, branchId, deviceId, "10 visitas", 8, true, null);
		AccessDecisionResponse granted = new AccessDecisionResponse(
				logId, "GRANTED", "VISIT_PACKAGE", "8 restantes", "SIMULATED_OPEN", OffsetDateTime.now(),
				device.name(), device.deviceCode(), clientId, client.fullName(), null, null, null);
		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(client));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(benefitId));
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(false, Reason.ACTIVE_MEMBERSHIP_REQUIRED, null, List.of()));
		when(visitPackages.authorizeForAccess(
				eq(organizationId), eq(branchId), eq(clientId), eq(List.of(benefitId)), eq(deviceId),
				eq("access:visit-1"), any())).thenReturn(Optional.of(consumption));
		when(repository.createAccessLog(
				eq(organizationId), eq(branchId), eq(deviceId), eq(clientId), eq(null), eq(null),
				eq("VISIT_PACKAGE"), eq("GRANTED"), eq("RECEPTION"), any(), eq("SIMULATED_OPEN"),
				any(), eq(userId), eq(correlationId))).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(granted));

		assertThat(service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null, "visit-1"),
				correlationId.toString()).accessType()).isEqualTo("VISIT_PACKAGE");

		verify(visitPackages).linkAccessLog(
				organizationId, consumptionId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, logId);
	}

	@Test
	void grantsIncludedPackageAccessWithoutReportingAnotherConsumption() {
		AccessRepository repository = mock(AccessRepository.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		VisitPackageAccessOperations visitPackages = mock(VisitPackageAccessOperations.class);
		AccessService service = service(repository, mock(AuditService.class), eligibilityPolicy, visitPackages);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID saunaId = UUID.randomUUID();
		UUID includedUsageId = UUID.randomUUID();
		UUID baseConsumptionId = UUID.randomUUID();
		UUID logId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "reception@gymos.local", "Recepcion", Set.of());
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Sauna", "SAUNA", "ZONE", "ACTIVE", "SIMULATED", null);
		ClientAccessRecord client = new ClientAccessRecord(clientId, "Socio", null, null, "ACTIVE");
		VisitPackageAccessResult includedUsage = new VisitPackageAccessResult(
				includedUsageId, baseConsumptionId, saunaId,
				VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, UUID.randomUUID(), clientId,
				branchId, deviceId, "10 visitas + Sauna", 9, true, null);
		AccessDecisionResponse granted = new AccessDecisionResponse(
				logId, "GRANTED", "VISIT_PACKAGE_INCLUDED", "Saldo 9", "SIMULATED_OPEN",
				OffsetDateTime.now(), device.name(), device.deviceCode(), clientId, client.fullName(),
				null, null, null);
		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(client));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(saunaId));
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(false, Reason.ACTIVE_MEMBERSHIP_REQUIRED, null, List.of()));
		when(visitPackages.authorizeForAccess(
				eq(organizationId), eq(branchId), eq(clientId), eq(List.of(saunaId)), eq(deviceId),
				eq("access:sauna-1"), any())).thenReturn(Optional.of(includedUsage));
		when(repository.createAccessLog(
				eq(organizationId), eq(branchId), eq(deviceId), eq(clientId), eq(null), eq(null),
				eq("VISIT_PACKAGE_INCLUDED"), eq("GRANTED"), eq("RECEPTION"), any(),
				eq("SIMULATED_OPEN"), any(), eq(userId), eq(correlationId))).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(granted));

		assertThat(service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null, "sauna-1"),
				correlationId.toString()).accessType()).isEqualTo("VISIT_PACKAGE_INCLUDED");

		verify(visitPackages).linkAccessLog(
				organizationId, includedUsageId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, logId);
	}

	@Test
	void recordsAndLinksIncludedPackageDenialWhenBaseVisitIsMissing() {
		AccessRepository repository = mock(AccessRepository.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		VisitPackageAccessOperations visitPackages = mock(VisitPackageAccessOperations.class);
		AccessService service = service(repository, mock(AuditService.class), eligibilityPolicy, visitPackages);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID saunaId = UUID.randomUUID();
		UUID usageId = UUID.randomUUID();
		UUID logId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "reception@gymos.local", "Recepcion", Set.of());
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Sauna", "SAUNA", "ZONE", "ACTIVE", "SIMULATED", null);
		ClientAccessRecord client = new ClientAccessRecord(clientId, "Socio", null, null, "ACTIVE");
		VisitPackageAccessResult deniedUsage = new VisitPackageAccessResult(
				usageId, null, saunaId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
				UUID.randomUUID(), clientId, branchId, deviceId, "10 visitas + Sauna", 9, false, null);
		AccessDecisionResponse denied = new AccessDecisionResponse(
				logId, "DENIED", "VISIT_PACKAGE_INCLUDED", "Requiere visita base", "NO_ACTION",
				OffsetDateTime.now(), device.name(), device.deviceCode(), clientId, client.fullName(),
				null, null, null);
		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(client));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(saunaId));
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(false, Reason.ACTIVE_MEMBERSHIP_REQUIRED, null, List.of()));
		when(visitPackages.authorizeForAccess(
				eq(organizationId), eq(branchId), eq(clientId), eq(List.of(saunaId)), eq(deviceId),
				eq("access:sauna-without-base"), any())).thenReturn(Optional.of(deniedUsage));
		when(repository.createAccessLog(
				eq(organizationId), eq(branchId), eq(deviceId), eq(clientId), eq(null), eq(null),
				eq("VISIT_PACKAGE_INCLUDED"), eq("DENIED"), eq("RECEPTION"), any(),
				eq("NOT_SENT"), any(), eq(userId), eq(correlationId))).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(denied));

		assertThat(service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null, "sauna-without-base"),
				correlationId.toString()).decision()).isEqualTo("DENIED");

		verify(visitPackages).linkAccessLog(
				organizationId, usageId, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, logId);
	}

	@Test
	void deniesRestrictedDeviceWhenCurrentPlanDoesNotGrantRequiredBenefit() {
		AccessRepository repository = mock(AccessRepository.class);
		AuditService auditService = mock(AuditService.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		AccessService service = service(repository, auditService, eligibilityPolicy);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID requiredBenefitId = UUID.randomUUID();
		UUID logId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "reception@gymos.local", "Recepcion", Set.of("access.manage"));
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Sauna", "SAUNA-01", "BIOMETRIC", "ACTIVE", "SIMULATED", null);
		ClientAccessRecord client = new ClientAccessRecord(clientId, "Socio", null, null, "ACTIVE");
		ActiveMembership membership = new ActiveMembership(
				membershipId, UUID.randomUUID(), "Plan basico", LocalDate.now().minusDays(1),
				LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));
		AccessDecisionResponse denied = new AccessDecisionResponse(
				logId, "DENIED", "BENEFIT_CHECK", "Sin acceso", "NOT_SENT", OffsetDateTime.now(),
				device.name(), device.deviceCode(), clientId, client.fullName(), membershipId,
				membership.planName(), null);

		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(client));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(requiredBenefitId));
		when(eligibilityPolicy.evaluate(any())).thenReturn(
				new EligibilityDecision(false, Reason.BENEFIT_NOT_GRANTED, membership, List.of()));
		when(repository.createAccessLog(
				eq(organizationId), eq(branchId), eq(deviceId), eq(clientId), eq(membershipId),
				eq(null), eq("BENEFIT_CHECK"), eq("DENIED"), eq("RECEPTION"), any(), eq("NOT_SENT"),
				any(), eq(userId), eq(correlationId)
		)).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(denied));

		AccessDecisionResponse result = service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null, "benefit-denied"), correlationId.toString());

		assertThat(result.decision()).isEqualTo("DENIED");
		verify(eligibilityPolicy).evaluate(any());
	}

	@Test
	void requiresDedicatedPermissionForManualOpening() {
		AccessService service = service(
				mock(AccessRepository.class), mock(AuditService.class), mock(EligibilityPolicy.class));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "reception@gymos.local", "Recepcion",
				Set.of("access.manage"));

		assertThatThrownBy(() -> service.manualOpen(
				principal, new ManualOpenCommand(null, null, "Prueba de seguridad"), UUID.randomUUID().toString()))
				.isInstanceOf(com.gymos.common.api.BusinessException.class)
				.hasMessageContaining("permiso");
	}

	@Test
	void returnsRequirementsOnlyForAnAccessPointInTheActiveBranch() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID requiredBenefitId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "reception@gymos.local", "Recepcion",
				Set.of("access.read"));
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Entrada principal", "MAIN-DOOR", "TURNSTILE", "ACTIVE", "HIKCENTRAL", null);

		when(repository.devices(organizationId, branchId)).thenReturn(List.of(device));
		when(repository.deviceById(organizationId, branchId, deviceId)).thenReturn(Optional.of(device));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(requiredBenefitId));

		assertThat(service.deviceRequiredBenefits(principal, deviceId)).containsExactly(requiredBenefitId);
		verify(repository).deviceById(organizationId, branchId, deviceId);
		verify(repository).deviceRequiredBenefitIds(organizationId, branchId, deviceId);
	}

	@Test
	void createsAnActiveAccessPointWithANormalizedCode() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UUID requiredBenefitId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "admin@gymos.local", "Administrador",
				Set.of("access.physical.manage"));
		AccessDeviceRecord created = new AccessDeviceRecord(
				deviceId, "Torniquete recepción", "FRONT-01", "TURNSTILE", "ACTIVE", "HIKCENTRAL", null);

		when(repository.deviceCodeExists(organizationId, "FRONT-01")).thenReturn(false);
		when(repository.accessBenefitsExist(organizationId, branchId, List.of(requiredBenefitId))).thenReturn(true);
		when(repository.createDevice(
				eq(organizationId), eq(branchId), eq("Torniquete recepción"), eq("FRONT-01"),
				eq("TURNSTILE"), eq("HIKCENTRAL"), eq(userId)))
				.thenReturn(created);

		AccessDeviceRecord result = service.createDevice(
				principal, "Torniquete recepción", "front-01", "turnstile", "HIKCENTRAL",
				requiredBenefitId, UUID.randomUUID().toString());

		assertThat(result).isEqualTo(created);
		verify(repository).createDevice(
				eq(organizationId), eq(branchId), eq("Torniquete recepción"), eq("FRONT-01"),
				eq("TURNSTILE"), eq("HIKCENTRAL"), eq(userId));
		verify(repository).replaceDeviceBenefits(
				organizationId, branchId, deviceId, userId, List.of(requiredBenefitId));
	}

	@Test
	void accessPointCannotInferHikCentralFromAnEmptyProvider() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "manager@gymos.local", "Gerente",
				Set.of("access.physical.manage"));

		assertThatThrownBy(() -> service.createDevice(
				principal, "Torniquete", "TURN-EXPLICIT", "TURNSTILE", "  ",
				UUID.randomUUID(), "test"))
				.isInstanceOfSatisfying(
						com.gymos.common.api.BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("PHYSICAL_ACCESS_PROVIDER_REQUIRED"));
	}

	@Test
	void explicitlySimulatedAccessPointCanRemainDiagnosticWithoutABenefit() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "manager@gymos.local", "Gerente",
				Set.of("access.physical.manage"));
		AccessDeviceRecord created = new AccessDeviceRecord(
				UUID.randomUUID(), "Simulador", "SIM-01", "DOOR", "ACTIVE", "SIMULATED", null);
		when(repository.createDevice(
				eq(organizationId), eq(branchId), eq("Simulador"), eq("SIM-01"),
				eq("DOOR"), eq("SIMULATED"), eq(userId))).thenReturn(created);

		assertThat(service.createDevice(
				principal, "Simulador", "SIM-01", "DOOR", "SIMULATED", null, "test"))
				.isEqualTo(created);
	}

	@Test
	void productiveAccessPointCannotBeCreatedWithoutOneAccessBenefit() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "manager@gymos.local", "Gerente",
				Set.of("access.physical.manage"));

		assertThatThrownBy(() -> service.createDevice(
				principal, "Torniquete", "TURN-01", "TURNSTILE", "HIKCENTRAL", null, "test"))
				.isInstanceOfSatisfying(
						com.gymos.common.api.BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_BENEFIT_REQUIRED"));
	}

	@Test
	void productiveAccessPointRejectsMultipleRequiredBenefits() {
		AccessRepository repository = mock(AccessRepository.class);
		AccessService service = service(
				repository, mock(AuditService.class), mock(EligibilityPolicy.class));
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "manager@gymos.local", "Gerente",
				Set.of("access.physical.manage"));
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Torniquete", "TURN-01", "TURNSTILE", "ACTIVE", "HIKCENTRAL", null);
		when(repository.deviceById(organizationId, branchId, deviceId)).thenReturn(Optional.of(device));

		assertThatThrownBy(() -> service.replaceDeviceBenefits(
				principal, deviceId, List.of(UUID.randomUUID(), UUID.randomUUID()), "test"))
				.isInstanceOfSatisfying(
						com.gymos.common.api.BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("PHYSICAL_ACCESS_SINGLE_BENEFIT_REQUIRED"));
	}

	private AccessService service(
			AccessRepository repository,
			AuditService auditService,
			EligibilityPolicy eligibilityPolicy
	) {
		return service(repository, auditService, eligibilityPolicy, mock(VisitPackageAccessOperations.class));
	}

	private AccessService service(
			AccessRepository repository,
			AuditService auditService,
			EligibilityPolicy eligibilityPolicy,
			VisitPackageAccessOperations visitPackages
	) {
		return new AccessService(
				repository,
				auditService,
				eligibilityPolicy,
				new ObjectMapper(),
				mock(PhysicalAccessRepository.class),
				mock(PhysicalAccessSynchronizationService.class),
				new PhysicalAccessProviderCatalog(),
				mock(PhysicalConnectorService.class),
				visitPackages
		);
	}
}
