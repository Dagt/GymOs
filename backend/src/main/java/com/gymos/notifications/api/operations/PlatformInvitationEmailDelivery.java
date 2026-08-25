package com.gymos.notifications.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PlatformInvitationEmailDelivery {
	InvitationEmailResult deliverOwnerInvitation(OwnerInvitationEmail command);

	record OwnerInvitationEmail(
			UUID invitationId,
			UUID organizationId,
			String organizationName,
			String ownerName,
			String ownerEmail,
			String replyTo,
			String invitationUrl,
			OffsetDateTime expiresAt
	) {
	}

	record InvitationEmailResult(String status) {
		public static InvitationEmailResult sent() {
			return new InvitationEmailResult("SENT");
		}

		public static InvitationEmailResult failed() {
			return new InvitationEmailResult("FAILED");
		}

		public static InvitationEmailResult notConfigured() {
			return new InvitationEmailResult("NOT_CONFIGURED");
		}
	}
}
