package com.gymos.clients.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.util.UUID;

/** Branch visibility for staff operations on a client record. */
public interface ClientBranchAccess {
	void requireVisible(GymOsPrincipal principal, UUID clientId);

	boolean hasVisibleEmail(GymOsPrincipal principal, String email);
}
