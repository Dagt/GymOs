package com.gymos.benefits.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.benefits.infrastructure.BenefitRepository.BenefitSummary;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BenefitServiceTests {
	@Test
	void packageCatalogReturnsActiveAccessBenefitsFromEveryOrganizationBranch() {
		BenefitRepository repository = mock(BenefitRepository.class);
		BenefitService service = new BenefitService(
				repository, mock(AuditService.class), mock(ApplicationEventPublisher.class));
		UUID organizationId = UUID.randomUUID();
		UUID centro = UUID.randomUUID();
		UUID tecnologica = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, tecnologica, "owner@gym.test", "Dueño",
				Set.of("catalog.read"));
		BenefitSummary saunaCentro = benefit(centro, "SAUNA", "ACCESS", "ACTIVE");
		BenefitSummary generalTecnologica = benefit(tecnologica, "GENERAL-TEC", "ACCESS", "ACTIVE");
		BenefitSummary inactiveAccess = benefit(centro, "CRYO", "ACCESS", "INACTIVE");
		BenefitSummary discount = benefit(centro, "STUDENT", "STUDENT", "ACTIVE");
		when(repository.benefits(organizationId)).thenReturn(List.of(
				saunaCentro, generalTecnologica, inactiveAccess, discount));

		assertThat(service.accessBenefitsForCatalog(principal))
				.containsExactly(saunaCentro, generalTecnologica);
	}

	private BenefitSummary benefit(UUID branchId, String code, String type, String status) {
		return new BenefitSummary(
				UUID.randomUUID(), branchId, code, code, type, "NONE", null, null,
				false, false, 0, null, null, status, List.of(), "LOCAL", List.of(),
				List.of(branchId), 0);
	}
}
