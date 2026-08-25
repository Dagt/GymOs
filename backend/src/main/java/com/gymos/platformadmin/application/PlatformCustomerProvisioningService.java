package com.gymos.platformadmin.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.api.operations.TenantOwnerProvisioning;
import com.gymos.notifications.api.operations.PlatformInvitationEmailDelivery;
import com.gymos.notifications.api.operations.PlatformInvitationEmailDelivery.OwnerInvitationEmail;
import com.gymos.platformadmin.application.PlatformCustomerService.CreateCustomerCommand;
import com.gymos.platformadmin.application.PlatformCustomerService.ProvisionedCustomer;
import com.gymos.platformadmin.application.PlatformCustomerService.ProvisionedOwnerInvitation;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlatformCustomerProvisioningService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlatformCustomerProvisioningService.class);

	private final PlatformCustomerService customers;
	private final PlatformInvitationEmailDelivery emailDelivery;
	private final TenantOwnerProvisioning ownerProvisioning;

	public PlatformCustomerProvisioningService(
			PlatformCustomerService customers,
			PlatformInvitationEmailDelivery emailDelivery,
			TenantOwnerProvisioning ownerProvisioning
	) {
		this.customers = customers;
		this.emailDelivery = emailDelivery;
		this.ownerProvisioning = ownerProvisioning;
	}

	public ProvisionedCustomer create(
			GymOsPrincipal principal,
			CreateCustomerCommand command,
			String correlationId
	) {
		ProvisionedCustomer provisioned = customers.create(principal, command, correlationId);
		String status = deliver(
				provisioned.invitationId(), provisioned.customer().organizationId(),
				provisioned.customer().tradeName(), provisioned.ownerName(), provisioned.ownerEmail(),
				provisioned.customer().contactEmail(), provisioned.invitationUrl(),
				provisioned.invitationExpiresAt());
		recordStatus(provisioned.invitationId(), status);
		return provisioned.withEmailDeliveryStatus(status);
	}

	public ProvisionedOwnerInvitation reissueOwnerInvitation(
			GymOsPrincipal principal,
			UUID organizationId,
			String correlationId
	) {
		ProvisionedOwnerInvitation invitation = customers.reissueOwnerInvitation(
				principal, organizationId, correlationId);
		String status = deliver(
				invitation.invitationId(), invitation.organizationId(), invitation.organizationName(),
				invitation.ownerName(), invitation.ownerEmail(), invitation.organizationContactEmail(),
				invitation.invitationUrl(), invitation.invitationExpiresAt());
		recordStatus(invitation.invitationId(), status);
		return invitation.withEmailDeliveryStatus(status);
	}

	private String deliver(
			UUID invitationId,
			UUID organizationId,
			String organizationName,
			String ownerName,
			String ownerEmail,
			String replyTo,
			String invitationUrl,
			OffsetDateTime expiresAt
	) {
		try {
			return emailDelivery.deliverOwnerInvitation(new OwnerInvitationEmail(
					invitationId, organizationId, organizationName, ownerName, ownerEmail,
					replyTo, invitationUrl, expiresAt)).status();
		} catch (RuntimeException exception) {
			LOGGER.warn(
					"Owner invitation email delivery failed safely. invitationId={}, type={}",
					invitationId, exception.getClass().getSimpleName());
			return "FAILED";
		}
	}

	private void recordStatus(UUID invitationId, String status) {
		try {
			ownerProvisioning.recordEmailDelivery(invitationId, status, OffsetDateTime.now());
		} catch (RuntimeException exception) {
			LOGGER.warn(
					"Owner invitation delivery status could not be persisted. invitationId={}, status={}, type={}",
					invitationId, status, exception.getClass().getSimpleName());
		}
	}
}
