package com.gymos.platformadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.api.operations.TenantOwnerProvisioning;
import com.gymos.notifications.api.operations.PlatformInvitationEmailDelivery;
import com.gymos.platformadmin.application.PlatformCustomerProvisioningService;
import com.gymos.platformadmin.application.PlatformCustomerService;
import com.gymos.platformadmin.application.PlatformCustomerService.CreateCustomerCommand;
import com.gymos.platformadmin.application.PlatformCustomerService.PlatformCustomerView;
import com.gymos.platformadmin.application.PlatformCustomerService.ProvisionedCustomer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformCustomerProvisioningServiceTests {
	private final PlatformCustomerService customers = mock(PlatformCustomerService.class);
	private final PlatformInvitationEmailDelivery emailDelivery = mock(PlatformInvitationEmailDelivery.class);
	private final TenantOwnerProvisioning ownerProvisioning = mock(TenantOwnerProvisioning.class);
	private final PlatformCustomerProvisioningService service = new PlatformCustomerProvisioningService(
			customers, emailDelivery, ownerProvisioning);

	@Test
	void successfulProvisioningReturnsTheActualEmailDeliveryStatus() {
		GymOsPrincipal principal = mock(GymOsPrincipal.class);
		CreateCustomerCommand command = command();
		ProvisionedCustomer provisioned = provisioned();
		when(customers.create(principal, command, "correlation")).thenReturn(provisioned);
		when(emailDelivery.deliverOwnerInvitation(any()))
				.thenReturn(PlatformInvitationEmailDelivery.InvitationEmailResult.sent());

		ProvisionedCustomer result = service.create(principal, command, "correlation");

		assertThat(result.emailDeliveryStatus()).isEqualTo("SENT");
		verify(ownerProvisioning).recordEmailDelivery(eq(provisioned.invitationId()), eq("SENT"), any());
	}

	@Test
	void postCommitDeliveryTrackingFailureNeverTurnsACreatedGymIntoAFalseHttpFailure() {
		GymOsPrincipal principal = mock(GymOsPrincipal.class);
		CreateCustomerCommand command = command();
		ProvisionedCustomer provisioned = provisioned();
		when(customers.create(principal, command, "correlation")).thenReturn(provisioned);
		when(emailDelivery.deliverOwnerInvitation(any()))
				.thenReturn(PlatformInvitationEmailDelivery.InvitationEmailResult.failed());
		doThrow(new IllegalStateException("simulated tracking failure"))
				.when(ownerProvisioning).recordEmailDelivery(any(), any(), any());

		ProvisionedCustomer result = service.create(principal, command, "correlation");

		assertThat(result.customer().organizationId()).isEqualTo(provisioned.customer().organizationId());
		assertThat(result.emailDeliveryStatus()).isEqualTo("FAILED");
	}

	private ProvisionedCustomer provisioned() {
		UUID organizationId = UUID.randomUUID();
		UUID invitationId = UUID.randomUUID();
		return new ProvisionedCustomer(
				new PlatformCustomerView(
						organizationId, "Gimnasio Norte", "Gimnasio Norte SA", "GNO260101AA1",
						"ACTIVE", "contacto@example.com", "+525512345678", "Gimnasio Norte", null,
						1, 1, "ONBOARDING", null, "contacto@example.com", "+525512345678",
						null, null, 0, null, null, 0, null, 0, BigDecimal.ZERO,
						0, 0, BigDecimal.ZERO, null, "SETUP_REQUIRED", null),
				invitationId, "Ana Dueña", "ana@example.com",
				"https://gymfort.mx/accept-invitation?token=one-time-token",
				OffsetDateTime.now().plusHours(72), "PENDING");
	}

	private CreateCustomerCommand command() {
		return new CreateCustomerCommand(
				"Gimnasio Norte SA", "Gimnasio Norte", "GNO260101AA1", "America/Mexico_City",
				"MXN", "contacto@example.com", "+525512345678", "Gimnasio Norte", null,
				"#093D3A", "#18C8B2", "#F2F8F7", "Sucursal Principal", "MAIN",
				"+525512345678", "Ana", "Dueña", "ana@example.com", "+525587654321");
	}
}
