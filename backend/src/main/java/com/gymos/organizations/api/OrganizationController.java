package com.gymos.organizations.api;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.BranchSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
	private final OrganizationQueryRepository organizationQueryRepository;

	public OrganizationController(OrganizationQueryRepository organizationQueryRepository) {
		this.organizationQueryRepository = organizationQueryRepository;
	}

	@GetMapping("/current")
	OrganizationSummary current(@AuthenticationPrincipal GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findCurrentOrganization(principal.organizationId())
				.orElseThrow(() -> new BusinessException(
						"ORGANIZATION_NOT_FOUND",
						"La organizacion de la sesion no esta disponible."
				));
	}

	@GetMapping("/current/branches")
	List<BranchSummary> branches(@AuthenticationPrincipal GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findBranchesForUser(principal.userId(), principal.organizationId());
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}
}
