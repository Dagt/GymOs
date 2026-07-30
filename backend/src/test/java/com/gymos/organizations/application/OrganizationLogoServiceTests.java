package com.gymos.organizations.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.organizations.application.OrganizationLogoService.UploadOrganizationLogo;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrganizationLogoServiceTests {
	private final OrganizationCommandRepository commands = mock(OrganizationCommandRepository.class);
	private final OrganizationQueryRepository organizations = mock(OrganizationQueryRepository.class);
	private final ObjectStorageService storage = mock(ObjectStorageService.class);
	private final AuditService audit = mock(AuditService.class);
	private final OrganizationLogoService service =
			new OrganizationLogoService(commands, organizations, storage, audit);

	@Test
	void rejectsExecutableSvgBeforeWritingStorage() {
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("organization.update")
		);

		assertThatThrownBy(() -> service.upload(
				principal,
				new UploadOrganizationLogo(
						"logo.svg",
						"image/svg+xml",
						4,
						new ByteArrayInputStream(new byte[] {1, 2, 3, 4})
				),
				"logo-test"
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code())
								.isEqualTo("ORGANIZATION_LOGO_INVALID"));

		verifyNoInteractions(commands, organizations, storage, audit);
	}

	@Test
	void storesVersionedLogoAndReplacesPreviousObjectAfterCommit() {
		UUID organizationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("organization.update")
		);
		var summary = new OrganizationQueryRepository.OrganizationSummary(
				organizationId,
				"Gym Legal",
				"Gym",
				"GYM010101ABC",
				"ACTIVE",
				"America/Mexico_City",
				"MXN",
				"contacto@gym.test",
				"+525512345678",
				"Gym",
				"/api/tenant-logo/" + organizationId + "?v=test",
				"#164E63",
				"#F59E0B",
				"#F4F7FB"
		);
		when(organizations.findLogoObjectKey(organizationId)).thenReturn(java.util.Optional.of("branding/old-logo.png"));
		when(commands.updateLogo(
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(userId),
				org.mockito.ArgumentMatchers.startsWith("/api/tenant-logo/" + organizationId + "?v="),
				org.mockito.ArgumentMatchers.startsWith("branding/" + organizationId + "/"),
				org.mockito.ArgumentMatchers.any()
		)).thenReturn(1);
		when(organizations.findCurrentOrganization(organizationId)).thenReturn(java.util.Optional.of(summary));

		TransactionSynchronizationManager.initSynchronization();
		try {
			service.upload(
					principal,
					new UploadOrganizationLogo(
							"brand.png",
							"image/png",
							4,
							new ByteArrayInputStream(new byte[] {1, 2, 3, 4})
					),
					"logo-success"
			);
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		verify(storage).putObject(
				org.mockito.ArgumentMatchers.startsWith("branding/" + organizationId + "/"),
				org.mockito.ArgumentMatchers.eq("image/png"),
				org.mockito.ArgumentMatchers.eq(4L),
				org.mockito.ArgumentMatchers.any()
		);
		verify(storage).removeObject("branding/old-logo.png");
		verify(audit).record(
				principal,
				"ORGANIZATION_LOGO_UPDATED",
				"ORGANIZATION",
				organizationId,
				"BACKOFFICE",
				null,
				"logo-success"
		);
	}
}
