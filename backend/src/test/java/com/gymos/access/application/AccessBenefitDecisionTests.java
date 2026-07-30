package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.access.application.command.AccessRequestCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.ActiveMembershipRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessBenefitDecisionTests {
	@Test
	void deniesRestrictedDeviceWhenCurrentPlanDoesNotGrantRequiredBenefit() {
		AccessRepository repository = mock(AccessRepository.class);
		AuditService auditService = mock(AuditService.class);
		AccessService service = new AccessService(repository, auditService);
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
		ActiveMembershipRecord membership = new ActiveMembershipRecord(
				membershipId, UUID.randomUUID(), "Plan basico", LocalDate.now().minusDays(1),
				LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));
		AccessDecisionResponse denied = new AccessDecisionResponse(
				logId, "DENIED", "BENEFIT_CHECK", "Sin acceso", "NOT_SENT", OffsetDateTime.now(),
				device.name(), device.deviceCode(), clientId, client.fullName(), membershipId,
				membership.planName(), null);

		when(repository.defaultDevice(organizationId, branchId)).thenReturn(Optional.of(device));
		when(repository.clientById(organizationId, clientId)).thenReturn(Optional.of(client));
		when(repository.activeMembership(eq(organizationId), eq(branchId), eq(clientId), any()))
				.thenReturn(Optional.of(membership));
		when(repository.deviceRequiredBenefitIds(organizationId, branchId, deviceId))
				.thenReturn(List.of(requiredBenefitId));
		when(repository.effectiveAccessBenefits(eq(organizationId), eq(branchId), eq(clientId), any()))
				.thenReturn(List.of());
		when(repository.createAccessLog(
				eq(organizationId), eq(branchId), eq(deviceId), eq(clientId), eq(membershipId),
				eq(null), eq("BENEFIT_CHECK"), eq("DENIED"), eq("RECEPTION"), any(), eq("NOT_SENT"),
				any(), eq(userId), eq(correlationId)
		)).thenReturn(logId);
		when(repository.decision(logId)).thenReturn(Optional.of(denied));

		AccessDecisionResponse result = service.requestAccess(
				principal, new AccessRequestCommand(clientId, null, null), correlationId.toString());

		assertThat(result.decision()).isEqualTo("DENIED");
		verify(repository).effectiveAccessBenefits(eq(organizationId), eq(branchId), eq(clientId), any());
	}
}
