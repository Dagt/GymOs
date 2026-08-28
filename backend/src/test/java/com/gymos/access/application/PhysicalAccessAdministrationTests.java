package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.application.command.ConfigurePhysicalIntegrationCommand;
import com.gymos.access.application.command.LinkPhysicalClientCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalProviderConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ClientLinkRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DeviceLinkRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.eligibility.api.EligibilityPolicy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalAccessAdministrationTests {
	private final AccessRepository accessRepository = mock(AccessRepository.class);
	private final PhysicalAccessRepository physicalRepository = mock(PhysicalAccessRepository.class);
	private final PhysicalAccessSynchronizationService synchronizationService =
			mock(PhysicalAccessSynchronizationService.class);
	private final PhysicalConnectorService connectorService = mock(PhysicalConnectorService.class);
	private final AccessService service = new AccessService(
			accessRepository,
			mock(AuditService.class),
			mock(EligibilityPolicy.class),
			new ObjectMapper(),
			physicalRepository,
			synchronizationService,
			new PhysicalAccessProviderCatalog(),
			connectorService,
			mock(com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.class)
	);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final UUID connectionId = UUID.randomUUID();
	private final GymOsPrincipal manager = new GymOsPrincipal(
			userId, organizationId, branchId, "manager@gymfort.local", "Gerente",
			Set.of("access.physical.manage"));
	private final ConnectionRecord connection = new ConnectionRecord(
			connectionId, organizationId, branchId, "HIKCENTRAL", "HikCentral Centro",
			"access-centro", true, "AWAITING_CONNECTOR", null, null, null,
			null, null, null, null);

	@BeforeEach
	void defaultLookups() {
		when(physicalRepository.connectionByConnectorKey("access-centro")).thenReturn(Optional.empty());
		when(accessRepository.physicalAccessBenefits(organizationId, branchId)).thenReturn(List.of());
		when(accessRepository.physicalBenefitMappings(organizationId, branchId, connectionId))
				.thenReturn(List.of());
		when(physicalRepository.deviceLinks(organizationId, branchId, connectionId)).thenReturn(List.of());
		when(connectorService.connectionHealth(connection)).thenReturn(
				new PhysicalConnectorService.ConnectionHealthView(
						"AWAITING_CONNECTOR", false, null, null, null, 0, 0, null));
	}

	@Test
	void configuresAnAvailableProviderAndReconcilesLinkedClients() {
		UUID benefitId = UUID.randomUUID();
		var command = new ConfigurePhysicalIntegrationCommand(
				true, "hikcentral", "HikCentral Centro", "access-centro",
				List.of(new ConfigurePhysicalIntegrationCommand.BenefitMapping(
						benefitId, "LEVEL-GYM", "Acceso general")));
		when(physicalRepository.connection(organizationId, branchId))
				.thenReturn(Optional.empty(), Optional.of(connection));
		when(physicalRepository.connection(organizationId, branchId, connectionId))
				.thenReturn(Optional.of(connection));
		when(accessRepository.accessBenefitsExist(organizationId, branchId, List.of(benefitId)))
				.thenReturn(true);
		when(accessRepository.upsertPhysicalProviderConnection(
				eq(organizationId), eq(branchId), eq("HIKCENTRAL"), eq("HikCentral Centro"),
				eq("access-centro"), eq(true), eq(userId)))
				.thenReturn(new PhysicalProviderConnectionRecord(
						connectionId, "HIKCENTRAL", "HikCentral Centro", "access-centro", true,
						"AWAITING_CONNECTOR", null, null, null));

		var result = service.configurePhysicalIntegration(manager, command, "correlation");

		assertThat(result.connection().providerKey()).isEqualTo("HIKCENTRAL");
		verify(accessRepository).replacePhysicalBenefitMappings(
				eq(organizationId), eq(branchId), eq(connectionId), any(), eq(userId));
		verify(synchronizationService).reconcileConnection(
				connection, "physical-configuration:correlation", null, "MAPPING_CONFIGURATION", userId);
	}

	@Test
	void plannedProviderCannotBeConfigured() {
		var command = new ConfigurePhysicalIntegrationCommand(
				true, "ZKTECO", "ZKTeco", "access-centro", List.of());

		assertThatThrownBy(() -> service.configurePhysicalIntegration(manager, command, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_PROVIDER_UNSUPPORTED"));
		verify(accessRepository, never()).upsertPhysicalProviderConnection(
				any(), any(), any(), any(), any(), anyBoolean(), any());
	}

	@Test
	void physicalConnectionRequiresAnExplicitProductiveProvider() {
		var command = new ConfigurePhysicalIntegrationCommand(
				false, " ", "Acceso centro", null, List.of());

		assertThatThrownBy(() -> service.configurePhysicalIntegration(manager, command, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("PHYSICAL_ACCESS_PROVIDER_REQUIRED"));
	}

	@Test
	void unconfiguredIntegrationDoesNotPreselectAHardwareBrand() {
		when(physicalRepository.connection(organizationId, branchId)).thenReturn(Optional.empty());

		var result = service.physicalIntegration(manager);

		assertThat(result.connection().id()).isNull();
		assertThat(result.connection().providerKey()).isEmpty();
		assertThat(result.connection().displayName()).isEmpty();
	}

	@Test
	void physicalConfigurationRequiresItsDedicatedPermission() {
		GymOsPrincipal reception = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "reception@gymfort.local", "Recepcion",
				Set.of("access.manage"));
		var command = new ConfigurePhysicalIntegrationCommand(
				true, "HIKCENTRAL", "HikCentral", "access-centro", List.of());

		assertThatThrownBy(() -> service.configurePhysicalIntegration(reception, command, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("ACCESS_PHYSICAL_MANAGE_FORBIDDEN"));
	}

	@Test
	void invisibleClientFromAnotherTenantOrBranchCannotBeLinked() {
		UUID clientId = UUID.randomUUID();
		when(accessRepository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.linkPhysicalClient(
				manager, clientId, new LinkPhysicalClientCommand("person-42"), "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("CLIENT_NOT_FOUND"));
		verify(accessRepository, never()).upsertPhysicalClientLink(any(), any(), any(), any(), any(), any());
	}

	@Test
	void externalPersonReferenceCannotBelongToTwoClients() {
		UUID clientId = UUID.randomUUID();
		UUID otherClientId = UUID.randomUUID();
		when(accessRepository.clientById(organizationId, branchId, clientId)).thenReturn(Optional.of(
				new ClientAccessRecord(clientId, "Socia Uno", null, null, "ACTIVE")));
		when(physicalRepository.connection(organizationId, branchId)).thenReturn(Optional.of(connection));
		when(physicalRepository.clientLinkByExternalRef(
				organizationId, branchId, connectionId, "person-42"))
				.thenReturn(Optional.of(new ClientLinkRecord(
						otherClientId, "person-42", OffsetDateTime.now())));

		assertThatThrownBy(() -> service.linkPhysicalClient(
				manager, clientId, new LinkPhysicalClientCommand("person-42"), "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_PERSON_DUPLICATE"));
	}

	@Test
	void externalDeviceReferenceCannotBelongToTwoPoints() {
		UUID deviceId = UUID.randomUUID();
		UUID otherDeviceId = UUID.randomUUID();
		AccessDeviceRecord device = new AccessDeviceRecord(
				deviceId, "Torniquete", "TURN-01", "TURNSTILE", "ACTIVE", "HIKCENTRAL", null);
		when(accessRepository.deviceById(organizationId, branchId, deviceId)).thenReturn(Optional.of(device));
		when(physicalRepository.connection(organizationId, branchId)).thenReturn(Optional.of(connection));
		when(physicalRepository.deviceLinkByExternalRef(
				organizationId, branchId, connectionId, "door-main"))
				.thenReturn(Optional.of(new DeviceLinkRecord(
						UUID.randomUUID(), otherDeviceId, "Otra", "TURN-02", "door-main", null,
						OffsetDateTime.now())));

		assertThatThrownBy(() -> service.linkPhysicalDevice(
				manager, deviceId, "door-main", "Principal", "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PHYSICAL_ACCESS_DEVICE_REF_DUPLICATE"));
	}

	@Test
	void benefitMappingFromAnotherTenantOrBranchIsRejected() {
		UUID foreignBenefitId = UUID.randomUUID();
		var command = new ConfigurePhysicalIntegrationCommand(
				true, "HIKCENTRAL", "HikCentral Centro", "access-centro",
				List.of(new ConfigurePhysicalIntegrationCommand.BenefitMapping(
						foreignBenefitId, "LEVEL-FOREIGN", null)));
		when(physicalRepository.connection(organizationId, branchId)).thenReturn(Optional.empty());
		when(accessRepository.accessBenefitsExist(
				organizationId, branchId, List.of(foreignBenefitId))).thenReturn(false);

		assertThatThrownBy(() -> service.configurePhysicalIntegration(manager, command, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("ACCESS_BENEFIT_NOT_AVAILABLE"));
	}

	@Test
	void deviceOutsideTheActiveBranchCannotBeLinked() {
		UUID foreignDeviceId = UUID.randomUUID();
		when(accessRepository.deviceById(organizationId, branchId, foreignDeviceId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.linkPhysicalDevice(
				manager, foreignDeviceId, "door-foreign", null, "test"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("ACCESS_DEVICE_NOT_FOUND"));
	}
}
